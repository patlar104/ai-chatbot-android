import express from 'express';
import { genkit, z } from 'genkit';
import { googleAI } from '@genkit-ai/google-genai';
import { enableFirebaseTelemetry } from '@genkit-ai/firebase';
import { SecretManagerServiceClient } from '@google-cloud/secret-manager';

enableFirebaseTelemetry();

const modelName = process.env.GENKIT_MODEL || 'gemini-2.5-flash';
const port = Number(process.env.PORT || process.env.GENKIT_PORT || 3400);

const ChatInputSchema = z.object({
  message: z.string().min(1),
  userId: z.string().optional(),
  sessionId: z.string().optional(),
});

const ChatOutputSchema = z.object({
  reply: z.string(),
  model: z.string(),
});

async function resolveGeminiApiKey() {
  const directKey = process.env.GEMINI_API_KEY?.trim();
  if (directKey) return directKey;

  const secretHint = process.env.GEMINI_API_KEY_SECRET?.trim();
  if (!secretHint) {
    throw new Error('Missing GEMINI_API_KEY or GEMINI_API_KEY_SECRET');
  }

  const secretVersionName = toSecretVersionName(secretHint);
  const client = new SecretManagerServiceClient();
  const [version] = await client.accessSecretVersion({ name: secretVersionName });
  const payload = version.payload?.data?.toString('utf8')?.trim();
  if (!payload) throw new Error('Secret exists but has empty value');
  return payload;
}

function toSecretVersionName(secretHint) {
  if (secretHint.startsWith('projects/')) {
    return secretHint.includes('/versions/')
      ? secretHint
      : `${secretHint}/versions/latest`;
  }

  const projectId =
    process.env.GCP_PROJECT ||
    process.env.GOOGLE_CLOUD_PROJECT ||
    process.env.PROJECT_ID;

  if (!projectId) {
    throw new Error(
      'GCP project not set. Set GCP_PROJECT (or GOOGLE_CLOUD_PROJECT) to use Secret Manager'
    );
  }

  return `projects/${projectId}/secrets/${secretHint}/versions/latest`;
}

function buildChatFlow(aiInstance) {
  return aiInstance.defineFlow(
    {
      name: 'chatFlow',
      inputSchema: ChatInputSchema,
      outputSchema: ChatOutputSchema,
    },
    async (input) => {
      const promptLines = [];
      if (input.userId) promptLines.push(`UserId: ${input.userId}`);
      if (input.sessionId) promptLines.push(`SessionId: ${input.sessionId}`);
      promptLines.push('You are a concise and helpful AI assistant.');
      promptLines.push(`User message: ${input.message.trim()}`);

      const response = await aiInstance.generate({
        model: googleAI.model(modelName),
        prompt: promptLines.join('\n'),
      });

      const rawText =
        typeof response.text === 'function' ? response.text() : response.text;
      const reply = String(rawText || '').trim();
      if (!reply) {
        throw new Error('Genkit returned an empty reply');
      }

      return {
        reply,
        model: modelName,
      };
    }
  );
}

const app = express();
app.use(express.json({ limit: '256kb' }));

app.get('/health', (_req, res) => {
  res.status(200).json({
    status: 'ok',
    service: 'genkit',
    model: modelName,
  });
});

async function main() {
  const geminiApiKey = await resolveGeminiApiKey();
  const ai = genkit({
    plugins: [googleAI({ apiKey: geminiApiKey })],
  });
  const chatFlow = buildChatFlow(ai);

  app.post('/chat', async (req, res) => {
    const parsed = ChatInputSchema.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({
        error: {
          code: 'bad_request',
          message: 'Body must include non-empty field "message"',
        },
      });
      return;
    }

    try {
      const output = await chatFlow(parsed.data);
      const validated = ChatOutputSchema.parse(output);
      res.status(200).json(validated);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Genkit upstream error';
      res.status(502).json({
        error: {
          code: 'upstream_failure',
          message,
        },
      });
    }
  });

  app.listen(port, '0.0.0.0', () => {
    console.log(`Genkit service listening on http://0.0.0.0:${port}`);
  });
}

main().catch((error) => {
  const message = error instanceof Error ? error.message : String(error);
  console.error(`Failed to start Genkit service: ${message}`);
  process.exit(1);
});
