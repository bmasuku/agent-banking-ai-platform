import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { StyleSheet, View } from 'react-native';
import { ListRow, Screen, SectionHeader } from '../../components';
import type { AgentStackParamList } from '../../navigation/types';
import { colors, spacing } from '../../theme/tokens';

type Nav = NativeStackNavigationProp<AgentStackParamList>;

export function AgentMoreScreen() {
  const navigation = useNavigation<Nav>();

  return (
    <Screen title="More" subtitle="Balances, rewards and account">
      <SectionHeader title="Balances" />
      <View style={styles.group}>
        <ListRow title="E-float" subtitle="Wallet float balance & statement" showChevron onPress={() => navigation.navigate('Efloat')} />
        <ListRow title="Cash in hand" subtitle="Physical cash ledger" showChevron onPress={() => navigation.navigate('CashInHand')} />
      </View>

      <SectionHeader title="Credit & rewards" />
      <View style={styles.group}>
        <ListRow title="Commission" subtitle="Accrued and paid commissions" showChevron onPress={() => navigation.navigate('Commission')} />
        <ListRow title="Credit score" subtitle="ML credit assessment" showChevron onPress={() => navigation.navigate('CreditScore')} />
        <ListRow title="Advance history" subtitle="Outstanding and past advances" showChevron onPress={() => navigation.navigate('AdvanceHistory')} />
      </View>

      <SectionHeader title="Account" />
      <View style={styles.group}>
        <ListRow title="Notifications" showChevron onPress={() => navigation.navigate('Notifications')} />
        <ListRow title="Profile" showChevron onPress={() => navigation.navigate('Profile')} />
        <ListRow
          title="Assistant"
          subtitle="Ask questions about your business"
          showChevron
          onPress={() => navigation.navigate('AgentTabs', { screen: 'AgentAssistant' })}
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