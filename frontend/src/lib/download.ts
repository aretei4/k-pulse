import { downloadBlob } from './apiClient';

/**
 * The Android shell's own download bridge, injected into the WebView.
 *
 * <p>The PDF is fetched with the agent's token, so it arrives as a Blob in the
 * page rather than as a URL the browser can hand to a download manager. A
 * WebView's DownloadListener never fires for a `blob:` URL, so inside the shell
 * the bytes are passed over this bridge instead and written to the phone's
 * Downloads folder by the app. A plain browser has no bridge and uses the
 * ordinary anchor download.
 */
interface DownloadBridge {
  /** Base64 of the file. Returns the saved content URI, or an empty string on failure. */
  save: (base64: string, fileName: string, mimeType: string) => string;
  /** Opens a file saved earlier by `save`. */
  open: (uri: string) => void;
}

declare global {
  interface Window {
    KPulseDownloads?: DownloadBridge;
  }
}

export function downloadBridge(): DownloadBridge | undefined {
  return typeof window === 'undefined' ? undefined : window.KPulseDownloads;
}

/** Whether the app can reopen a saved file — only the shell can. */
export function canOpenAfterSaving() {
  return Boolean(downloadBridge());
}

async function toBase64(blob: Blob) {
  const buffer = new Uint8Array(await blob.arrayBuffer());
  // In chunks: a whole booth's PDF is megabytes, and String.fromCharCode
  // applied to that many arguments at once overflows the call stack.
  const chunk = 0x8000;
  let binary = '';
  for (let i = 0; i < buffer.length; i += chunk) {
    binary += String.fromCharCode(...buffer.subarray(i, i + chunk));
  }
  return btoa(binary);
}

/**
 * Saves a file where the person will find it: the phone's Downloads folder in
 * the shell, the browser's own download otherwise.
 *
 * @returns a handle to reopen it with, when the platform offers one
 */
export async function saveFile(blob: Blob, fileName: string, mimeType = 'application/pdf'): Promise<string | null> {
  const bridge = downloadBridge();
  if (!bridge) {
    downloadBlob(blob, fileName);
    return null;
  }
  const uri = bridge.save(await toBase64(blob), fileName, mimeType);
  if (!uri) {
    throw new Error('The file could not be saved to Downloads');
  }
  return uri;
}

/** Opens a file saved by {@link saveFile}. Does nothing outside the shell. */
export function openSavedFile(uri: string | null) {
  const bridge = downloadBridge();
  if (bridge && uri) {
    bridge.open(uri);
  }
}
