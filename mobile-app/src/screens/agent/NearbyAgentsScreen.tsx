import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import { agentApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import { EmptyState, ErrorBanner, ListRow, LoadingBlock, Screen } from '../../components';
import { distanceKm, requestForegroundLocation } from '../../services/location';
import { colors, spacing } from '../../theme/tokens';

type AgentPeer = {
  id?: string;
  agentId?: string;
  agentCode: string;
  businessName: string;
  fullName?: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  distanceKm?: number;
  efloatLiquidity?: string;
  cashLiquidity?: string;
};

export function NearbyAgentsScreen() {
  const query = useQuery({
    queryKey: ['agent', 'peers'],
    queryFn: async () => {
      const [peers, loc] = await Promise.all([
        agentApi.peers() as Promise<AgentPeer[]>,
        requestForegroundLocation(),
      ]);
      return { peers, loc };
    },
  });

  const sorted = useMemo(() => {
    const peers = query.data?.peers ?? [];
    const loc = query.data?.loc;
    if (!loc) return peers;

    return [...peers].sort((a, b) => {
      const da =
        a.distanceKm ??
        (a.latitude != null && a.longitude != null
          ? distanceKm(loc, { latitude: a.latitude, longitude: a.longitude })
          : Number.POSITIVE_INFINITY);
      const db =
        b.distanceKm ??
        (b.latitude != null && b.longitude != null
          ? distanceKm(loc, { latitude: b.latitude, longitude: b.longitude })
          : Number.POSITIVE_INFINITY);
      return da - db;
    });
  }, [query.data]);

  return (
    <Screen
      title="Nearby agents"
      subtitle="Peer agents in your area for float exchanges"
      refreshing={query.isRefetching}
      onRefresh={() => query.refetch()}>
      {query.isError ? <ErrorBanner message={errorMessage(query.error)} /> : null}
      {query.isLoading ? <LoadingBlock label="Finding peers…" /> : null}
      {!query.isLoading && sorted.length === 0 ? (
        <EmptyState message="No peer agents found in your area." />
      ) : null}
      <View style={styles.list}>
        {sorted.map((peer) => {
          const loc = query.data?.loc;
          const km =
            peer.distanceKm ??
            (loc && peer.latitude != null && peer.longitude != null
              ? distanceKm(loc, { latitude: peer.latitude, longitude: peer.longitude })
              : null);
          const distanceLabel = km != null && Number.isFinite(km) ? `${km.toFixed(1)} km` : '—';
          return (
            <View key={peer.agentId ?? peer.id ?? peer.agentCode} style={styles.item}>
              <ListRow
                title={peer.businessName}
                subtitle={`${peer.agentCode}${peer.address ? ` · ${peer.address}` : ''}${peer.efloatLiquidity ? `\nE-float: ${peer.efloatLiquidity} · Cash: ${peer.cashLiquidity ?? '—'}` : ''}`}
                value={distanceLabel}
              />
            </View>
          );
        })}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacing.xs },
  item: {
    backgroundColor: colors.white,
    borderRadius: 12,
    paddingHorizontal: spacing.md,
  },
});