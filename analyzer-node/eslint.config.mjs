import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist/**', 'src/protocol/generated/**', 'node_modules/**'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['scripts/**/*.mjs'],
    languageOptions: { globals: { console: 'readonly', process: 'readonly' } }
  },
  {
    rules: {
      // Lõi phân tích phải tất định: cấm Math.random và Date.now
      'no-restricted-properties': [
        'error',
        { object: 'Math', property: 'random', message: 'Không dùng Math.random trong lõi phân tích (tính tất định).' },
        { object: 'Date', property: 'now', message: 'Không dùng thời gian hiện tại trong lõi phân tích (tính tất định).' }
      ],
      // Sidecar không được gọi mạng
      'no-restricted-imports': [
        'error',
        ...['http', 'https', 'node:http', 'node:https', 'net', 'node:net'].map((name) => ({
          name,
          message: 'Sidecar không được gọi mạng.'
        }))
      ]
    }
  }
);
