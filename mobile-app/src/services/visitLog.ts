import AsyncStorage from '@react-native-async-storage/async-storage';

const KEY = 'ab_field_visits_v2';

export type FieldVisit = {
  id: string;
  serverId?: string;
  agentId: string;
  agentCode?: string;
  agentName?: string;
  note: string;
  latitude: number;
  longitude: number;
  simulated: boolean;
  synced: boolean;
  syncError?: string;
  createdAt: string;
};

async function readAll(): Promise<FieldVisit[]> {
  try {
    const raw = await AsyncStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as FieldVisit[]) : [];
  } catch {
    return [];
  }
}

async function writeAll(visits: FieldVisit[]) {
  await AsyncStorage.setItem(KEY, JSON.stringify(visits));
}

export async function listVisits(): Promise<FieldVisit[]> {
  const all = await readAll();
  return all.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export async function addVisit(
  visit: Omit<FieldVisit, 'id' | 'createdAt' | 'synced' | 'serverId' | 'syncError'> & {
    synced?: boolean;
    serverId?: string;
  },
): Promise<FieldVisit> {
  const entry: FieldVisit = {
    ...visit,
    id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    createdAt: new Date().toISOString(),
    synced: visit.synced ?? false,
    serverId: visit.serverId,
  };
  const all = await readAll();
  all.unshift(entry);
  await writeAll(all.slice(0, 200));
  return entry;
}

export async function markVisitSynced(
  localId: string,
  serverId: string,
): Promise<FieldVisit | null> {
  const all = await readAll();
  const idx = all.findIndex((v) => v.id === localId);
  if (idx < 0) return null;
  all[idx] = {
    ...all[idx],
    synced: true,
    serverId,
    syncError: undefined,
  };
  await writeAll(all);
  return all[idx];
}

export async function markVisitSyncFailed(localId: string, message: string): Promise<void> {
  const all = await readAll();
  const idx = all.findIndex((v) => v.id === localId);
  if (idx < 0) return;
  all[idx] = {
    ...all[idx],
    synced: false,
    syncError: message,
  };
  await writeAll(all);
}

/** Merge server visits into the local cache (server wins for matching serverId). */
export async function mergeServerVisits(
  serverVisits: Array<{
    id: string;
    agentId: string;
    agentCode?: string;
    agentName?: string;
    notes: string;
    latitude?: number | null;
    longitude?: number | null;
    locationSimulated?: boolean;
    visitedAt: string;
  }>,
): Promise<FieldVisit[]> {
  const local = await readAll();
  const byServerId = new Map(local.filter((v) => v.serverId).map((v) => [v.serverId!, v]));
  for (const s of serverVisits) {
    const existing = byServerId.get(s.id);
    const mapped: FieldVisit = {
      id: existing?.id ?? `server-${s.id}`,
      serverId: s.id,
      agentId: s.agentId,
      agentCode: s.agentCode,
      agentName: s.agentName,
      note: s.notes,
      latitude: Number(s.latitude ?? 0),
      longitude: Number(s.longitude ?? 0),
      simulated: Boolean(s.locationSimulated),
      synced: true,
      createdAt: s.visitedAt,
    };
    byServerId.set(s.id, mapped);
  }
  const unsynced = local.filter((v) => !v.synced);
  const synced = [...byServerId.values()];
  const merged = [...unsynced, ...synced].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  await writeAll(merged.slice(0, 200));
  return merged;
}
