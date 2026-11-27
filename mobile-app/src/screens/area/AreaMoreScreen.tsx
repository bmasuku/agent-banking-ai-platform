import { CompositeNavigationProp, useNavigation } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { StyleSheet, View } from 'react-native';
import { Card, ListRow, Screen, SectionHeader } from '../../components';
import type { AreaStackParamList, AreaTabParamList } from '../../navigation/types';
import { spacing } from '../../theme/tokens';

type Nav = CompositeNavigationProp<
  BottomTabNavigationProp<AreaTabParamList, 'AreaMore'>,
  NativeStackNavigationProp<AreaStackParamList>
>;

export function AreaMoreScreen() {
  const navigation = useNavigation<Nav>();

  return (
    <Screen title="More" subtitle="Area manager tools and account">
      <SectionHeader title="Operations" />
      <Card style={styles.card}>
        <ListRow
          title="Field visits"
          subtitle="Device-local check-in log (not synced to backend)"
          showChevron
          onPress={() => navigation.navigate('FieldVisits')}
        />
        <ListRow
          title="Replenish float"
          subtitle="Top up agent e-float or cash"
          showChevron
          onPress={() => navigation.navigate('Replenish', {})}
        />
        <ListRow
          title="Float predictions"
          subtitle="Liquidity outlook and top-up guidance"
          showChevron
          onPress={() => navigation.navigate('FloatPredictions')}
        />
        <ListRow
          title="Exchange monitor"
          subtitle="Peer float exchanges"
          showChevron
          onPress={() => navigation.navigate('ExchangeMonitor')}
        />
        <ListRow
          title="Area performance"
          subtitle="Pools, flags and activity"
          showChevron
          onPress={() => navigation.navigate('AreaPerformance')}
        />
      </Card>

      <SectionHeader title="Account" />
      <Card style={styles.card}>
        <ListRow
          title="Notifications"
          subtitle="Platform alerts"
          showChevron
          onPress={() => navigation.navigate('Notifications')}
        />
        <ListRow
          title="Profile"
          subtitle="Your account details"
          showChevron
          onPress={() => navigation.navigate('Profile')}
        />
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    marginBottom: spacing.lg,
  },
});