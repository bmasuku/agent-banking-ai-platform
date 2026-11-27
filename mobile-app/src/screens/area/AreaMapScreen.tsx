import { CompositeNavigationProp, useNavigation } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import MapView, { Marker, Region } from 'react-native-maps';
import { areaApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import { EmptyState, ErrorBanner, LoadingBlock, Screen } from '../../components';
import type { AreaStackParamList, AreaTabParamList } from '../../navigation/types';
import { SIMULATED_COORDS } from '../../services/location';
import { colors, spacing, typography } from '../../theme/tokens';
import { money } from '../../utils/money';

type Nav = CompositeNavigationProp<
  BottomTabNavigationProp<AreaTabParamList, 'AreaMap'>,
  NativeStackNavigationProp<AreaStackParamList>
>;

export function AreaMapScreen() {
  const navigation = useNavigation<Nav>();

  const areaQ = useQuery({ queryKey: ['area', 'meta'], queryFn: areaApi.area });
  const locQ = useQuery({ queryKey: ['area', 'locations'], queryFn: areaApi.locations });

  const region: Region = useMemo(() => {
    const area = areaQ.data;
    const latitude = area?.centerLatitude ?? SIMULATED_COORDS.latitude;
    const longitude = area?.centerLongitude ?? SIMULATED_COORDS.longitude;
    return {
      latitude,
      longitude,
      latitudeDelta: 0.12,
      longitudeDelta: 0.12,
    };
  }, [areaQ.data]);

  const locations = locQ.data ?? [];

  return (
    <Screen
      scroll={false}
      padded={false}
      title="Area map"
      subtitle="Marker colour reflects e-float health"
      edges={['top', 'left', 'right', 'bottom']}
      refreshing={areaQ.isRefetching || locQ.isRefetching}
      onRefresh={() => {
        void areaQ.refetch();
        void locQ.refetch();
      }}>
      {locQ.isError ? <ErrorBanner message={errorMessage(locQ.error)} /> : null}
      <View style={styles.mapWrap}>
        {locQ.isLoading ? (
          <LoadingBlock label="Loading locations…" />
        ) : locations.length === 0 ? (
          <View style={styles.emptyWrap}>
            <EmptyState
              title="No agent locations"
              message="Assigned agents with coordinates will appear on this map with e-float health markers."
            />
          </View>
        ) : (
          <MapView style={styles.map} initialRegion={region} showsUserLocation>
            {locations.map((loc) => (
              <Marker
                key={loc.agentId}
                coordinate={{ latitude: loc.latitude, longitude: loc.longitude }}
                title={loc.businessName}
                description={`${loc.agentCode} · E-float ${money(loc.efloat)}`}
                pinColor={loc.efloatLow ? colors.danger : colors.success}
                onCalloutPress={() =>
                  navigation.navigate('AgentProfile', { agentId: loc.agentId })
                }
              />
            ))}
          </MapView>
        )}
      </View>
      <View style={styles.legend}>
        <View style={[styles.dot, { backgroundColor: colors.success }]} />
        <Text style={styles.legendText}>Healthy e-float</Text>
        <View style={[styles.dot, { backgroundColor: colors.danger }]} />
        <Text style={styles.legendText}>Low e-float</Text>
        {Platform.OS === 'android' ? (
          <Text style={styles.legendHint}>Tap a marker callout for agent profile</Text>
        ) : null}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  mapWrap: {
    flex: 1,
    minHeight: 360,
    marginHorizontal: spacing.lg,
    borderRadius: 16,
    overflow: 'hidden',
    backgroundColor: colors.sandDeep,
  },
  emptyWrap: {
    flex: 1,
    justifyContent: 'center',
    padding: spacing.lg,
  },
  map: {
    flex: 1,
    width: '100%',
  },
  legend: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: spacing.sm,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  dot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  legendText: {
    ...typography.caption,
    color: colors.inkMuted,
    marginRight: spacing.md,
  },
  legendHint: {
    ...typography.caption,
    color: colors.inkFaint,
    width: '100%',
  },
});