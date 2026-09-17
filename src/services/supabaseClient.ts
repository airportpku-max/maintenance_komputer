import { createClient, SupabaseClient } from '@supabase/supabase-js';

/**
 * Sanitizes the Supabase URL to ensure it never causes PostgREST error PGRST125
 * (Invalid path specified in request URL) which happens when /rest/v1 or trailing slashes are appended.
 */
export function sanitizeSupabaseUrl(rawUrl?: string): string {
  if (!rawUrl || typeof rawUrl !== 'string') {
    return 'https://onnyahegrkdtwysqptjw.supabase.co';
  }

  let url = rawUrl.trim().replace(/^["']|["']$/g, '');

  // If user pasted a Supabase dashboard URL: https://supabase.com/dashboard/project/<ref>
  const dashboardMatch = url.match(/supabase\.com\/dashboard\/project\/([a-z0-9]+)/i);
  if (dashboardMatch && dashboardMatch[1]) {
    url = `https://${dashboardMatch[1]}.supabase.co`;
  }

  // If user entered just the project ref like 'onnyahegrkdtwysqptjw'
  if (/^[a-z0-9]{20}$/i.test(url)) {
    url = `https://${url}.supabase.co`;
  }

  // Ensure protocol
  if (!/^https?:\/\//i.test(url)) {
    url = `https://${url}`;
  }

  // Remove trailing slashes
  url = url.replace(/\/+$/, '');

  // Strip /rest/v1 or /rest because @supabase/supabase-js automatically appends /rest/v1 internally
  url = url.replace(/\/rest\/v1\/?$/i, '');
  url = url.replace(/\/rest\/?$/i, '');

  return url.replace(/\/+$/, '');
}

export function sanitizeSupabaseKey(rawKey?: string): string {
  if (!rawKey || typeof rawKey !== 'string') {
    return 'sb_publishable_YCuQrEcGjyzVoguTmB5R2Q_jTHGpoim';
  }
  return rawKey.trim().replace(/^["']|["']$/g, '');
}

export function getStoredSupabaseUrl(): string {
  if (typeof window !== 'undefined') {
    const custom = localStorage.getItem('cms_custom_supabase_url');
    if (custom) return sanitizeSupabaseUrl(custom);
  }
  return sanitizeSupabaseUrl(import.meta.env.VITE_SUPABASE_URL || 'https://onnyahegrkdtwysqptjw.supabase.co');
}

export function getStoredSupabaseKey(): string {
  if (typeof window !== 'undefined') {
    const custom = localStorage.getItem('cms_custom_supabase_key');
    if (custom) return sanitizeSupabaseKey(custom);
  }
  return sanitizeSupabaseKey(import.meta.env.VITE_SUPABASE_ANON_KEY || 'sb_publishable_YCuQrEcGjyzVoguTmB5R2Q_jTHGpoim');
}

// Active Configuration
export let SUPABASE_URL = getStoredSupabaseUrl();
export let SUPABASE_ANON_KEY = getStoredSupabaseKey();

// Initialize Supabase Client
export let supabase: SupabaseClient = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
  },
});

/**
 * Updates the Supabase configuration dynamically
 */
export function setSupabaseConfig(newUrl?: string, newKey?: string) {
  if (newUrl !== undefined) {
    const cleanUrl = sanitizeSupabaseUrl(newUrl);
    SUPABASE_URL = cleanUrl;
    if (typeof window !== 'undefined') {
      localStorage.setItem('cms_custom_supabase_url', cleanUrl);
    }
  }

  if (newKey !== undefined) {
    const cleanKey = sanitizeSupabaseKey(newKey);
    SUPABASE_ANON_KEY = cleanKey;
    if (typeof window !== 'undefined') {
      localStorage.setItem('cms_custom_supabase_key', cleanKey);
    }
  }

  supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    auth: {
      persistSession: true,
      autoRefreshToken: true,
    },
  });
}

/**
 * Checks connection health to Supabase
 */
export async function testSupabaseConnection(): Promise<{ success: boolean; message: string; deviceCount?: number }> {
  try {
    // Ensure URL is clean before testing
    const currentUrl = sanitizeSupabaseUrl(SUPABASE_URL);
    if (currentUrl !== SUPABASE_URL) {
      setSupabaseConfig(currentUrl, SUPABASE_ANON_KEY);
    }

    const { data, error } = await supabase
      .from('devices')
      .select('id', { count: 'exact', head: false })
      .limit(1);

    if (error) {
      if (error.message.includes('Invalid path specified in request URL')) {
        return {
          success: false,
          message: 'Error PGRST125: Path URL tidak valid. URL Supabase harus berupa root domain (misal: https://[project-ref].supabase.co) tanpa tambahan "/rest/v1". Sistem telah menormalkan URL secara otomatis, silakan coba lagi.'
        };
      }
      return { success: false, message: `Error Supabase: ${error.message}` };
    }

    // Get total count of devices in database
    const totalResp = await supabase
      .from('devices')
      .select('*', { count: 'exact', head: true });

    return {
      success: true,
      message: `Terhubung ke Supabase (${currentUrl})`,
      deviceCount: totalResp.count ?? (data ? data.length : 0)
    };
  } catch (err: any) {
    return {
      success: false,
      message: err?.message || 'Gagal menghubungi Supabase'
    };
  }
}
