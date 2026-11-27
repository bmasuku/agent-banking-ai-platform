import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { StyleSheet, View } from 'react-native';
import { ListRow, Screen, SectionHeader } from '../../components';
import type { AgentStackParamList } from '../../navigation/types';
import { colors, spacing } from '../../theme/tokens';

type Nav = NativeStackNavigationProp<AgentStackParamList>;

export function AgentOpsScreen() {
  const navigation = useNavigation<Nav>();

  return (
    <Screen title="Operations" subtitle="Customer and float actions">
      <SectionHeader title="Customer transactions" />
      <View style={styles.group}>
        <ListRow
          title="Cash in"
          subtitle="Take cash and credit the customer's wallet"
          showChevron
          onPress={() => navigation.navigate('CashDeposit')}
        />
        <ListRow
          title="Cash out"
          subtitle="Customer receives cash from their wallet"
          showChevron
          onPress={() => navigation.navigate('Withdrawal')}
        />
      </View>

      <SectionHeader title="Float & credit" />
      <View style={styles.group}>
        <ListRow
          title="Float exchange"
          subtitle="Swap e-float and cash with a peer agent"
          showChevron
          onPress={() => navigation.navigate('FloatExchange')}
        />
        <ListRow
          title="Nearby agents"
          subtitle="Find peers for exchanges"
          showChevron
          onPress={() => navigation.navigate('NearbyAgents')}
        />
        <ListRow
          title="Request advance"
          subtitle="Working-capital advance from the bank"
          showChevron
          onPress={() => navigation.navigate('RequestAdvance')}
        />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  group: {
    backgroundColor: colors.white,
    borderRadius: 12,
    paddingHorizontal: spacing.md,
    marginBottom: spacing.lg,
  },
});