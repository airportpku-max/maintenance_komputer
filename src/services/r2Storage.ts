// Cloudflare R2 Worker Storage Service

export const R2_WORKER_URL = 
  (import.meta.env.VITE_CLOUDFLARE_WORKER_URL || 
   import.meta.env.VITE_R2_WORKER_URL || 
   'https://storagedatabasekomputer.airportpku.workers.dev').replace(/\/$/, '');

export interface R2UploadResult {
  success: boolean;
  url: string;
  fileKey: string;
  error?: string;
}

/**
 * Upload a File or Blob directly to Cloudflare R2 via Worker
 */
export async function uploadFileToR2(
  file: File | Blob, 
  customFilename?: string
): Promise<R2UploadResult> {
  try {
    const formData = new FormData();
    const fileName = customFilename || (file instanceof File ? file.name : `file_${Date.now()}.bin`);
    
    // Ensure Blob has filename attached if not a File instance
    const uploadableFile = file instanceof File ? file : new File([file], fileName, { type: file.type || 'application/octet-stream' });
    formData.append('file', uploadableFile);

    const response = await fetch(`${R2_WORKER_URL}/upload`, {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      throw new Error(`Upload gagal dengan status HTTP ${response.status}`);
    }

    const data = await response.json();
    if (data.success && data.fileKey) {
      // Build the direct public URL served by the worker
      const publicUrl = `${R2_WORKER_URL}/${data.fileKey}`;
      return {
        success: true,
        url: publicUrl,
        fileKey: data.fileKey,
      };
    }

    return {
      success: false,
      url: '',
      fileKey: '',
      error: data.error || 'Respon R2 tidak menyertakan status sukses',
    };
  } catch (err: any) {
    console.error('Error uploadFileToR2:', err);
    return {
      success: false,
      url: '',
      fileKey: '',
      error: err?.message || 'Gagal mengunggah file ke Cloudflare R2',
    };
  }
}

/**
 * Upload Base64 data URL to Cloudflare R2
 */
export async function uploadBase64ToR2(
  base64DataUrl: string, 
  fileName: string
): Promise<R2UploadResult> {
  try {
    // If it's already an HTTP / R2 URL, return it directly
    if (base64DataUrl.startsWith('http://') || base64DataUrl.startsWith('https://')) {
      const fileKey = base64DataUrl.split('/').pop() || '';
      return {
        success: true,
        url: base64DataUrl,
        fileKey,
      };
    }

    // Convert data URL to Blob
    const arr = base64DataUrl.split(',');
    const mimeMatch = arr[0].match(/:(.*?);/);
    const mime = mimeMatch ? mimeMatch[1] : 'image/jpeg';
    const bstr = atob(arr[1] || '');
    let n = bstr.length;
    const u8arr = new Uint8Array(n);
    while (n--) {
      u8arr[n] = bstr.charCodeAt(n);
    }
    const blob = new Blob([u8arr], { type: mime });

    return await uploadFileToR2(blob, fileName);
  } catch (err: any) {
    console.error('Error uploadBase64ToR2:', err);
    return {
      success: false,
      url: '',
      fileKey: '',
      error: err?.message || 'Gagal mengonversi & mengunggah base64 ke R2',
    };
  }
}

/**
 * Delete a file from Cloudflare R2 by fileKey or public URL
 */
export async function deleteFileFromR2(fileKeyOrUrl: string): Promise<boolean> {
  try {
    if (!fileKeyOrUrl) return false;
    // Extract fileKey from URL if full URL is passed
    let fileKey = fileKeyOrUrl;
    if (fileKeyOrUrl.includes('/')) {
      fileKey = fileKeyOrUrl.split('/').pop() || fileKeyOrUrl;
    }

    const response = await fetch(`${R2_WORKER_URL}/${fileKey}`, {
      method: 'DELETE',
    });

    if (!response.ok) return false;
    const data = await response.json();
    return !!data.success;
  } catch (err) {
    console.error('Error deleteFileFromR2:', err);
    return false;
  }
}

/**
 * Tests connection to Cloudflare R2 Worker
 */
export async function testR2Connection(): Promise<{ success: boolean; message: string }> {
  try {
    const response = await fetch(`${R2_WORKER_URL}/`, { method: 'GET' });
    if (response.ok) {
      const text = await response.text();
      return {
        success: true,
        message: text.trim() || 'Cloudflare R2 Worker Aktif',
      };
    }
    return {
      success: false,
      message: `HTTP ${response.status} dari R2 Worker`,
    };
  } catch (err: any) {
    return {
      success: false,
      message: err?.message || 'Tidak dapat terhubung ke Cloudflare R2 Worker',
    };
  }
}
