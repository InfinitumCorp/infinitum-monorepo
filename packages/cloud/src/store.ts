/**
 * Cumulus://Cloud store scaffold.
 * Object storage with licensing metadata.
 */
export interface CloudObject {
  key: string;
  owner: string;
  size: number;
  license: string;
  createdAt: string;
}

export class CloudStore {
  private objects = new Map<string, CloudObject>();

  put(key: string, owner: string, size: number, license = "personal"): CloudObject {
    const obj: CloudObject = { key, owner, size, license, createdAt: new Date().toISOString() };
    this.objects.set(key, obj);
    return obj;
  }

  get(key: string): CloudObject | undefined {
    return this.objects.get(key);
  }

  list(owner: string): CloudObject[] {
    return [...this.objects.values()].filter((o) => o.owner === owner);
  }
}
