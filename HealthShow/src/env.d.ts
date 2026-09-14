/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_BASE_API?: string
  readonly VITE_SAFETY_COMMAND_V2?: string
  readonly VITE_UNIFIED_CONTROL_V2?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

interface Window {
  __HEALTH_DEV_SW_CLEANUP__?: Promise<unknown>
}
