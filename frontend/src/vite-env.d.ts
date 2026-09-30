/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_USE_MOCKS?: string;
  readonly VITE_PRE_ELECTION_ONLY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
