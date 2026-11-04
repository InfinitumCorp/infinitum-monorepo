/**
 * VEIL Tor proxy scaffold.
 * In production this would route fetches through a local Tor SOCKS proxy
 * (e.g. 127.0.0.1:9050) or a privacy-preserving fetch service.
 */
export interface TorFetchOptions {
  url: string;
  method?: string;
  headers?: Record<string, string>;
  body?: string;
}

export async function torFetch(opts: TorFetchOptions): Promise<{ status: number; body: string }> {
  // Scaffold: direct fetch. Production replaces this with a SOCKS agent.
  const res = await fetch(opts.url, {
    method: opts.method ?? "GET",
    headers: opts.headers,
    body: opts.body,
  });
  return { status: res.status, body: await res.text() };
}

export const TOR_PROXY = process.env.TOR_SOCKS ?? "socks5://127.0.0.1:9050";
