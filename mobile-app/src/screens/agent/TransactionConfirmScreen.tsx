import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { StyleSheet, Text, View } from 'react-native';
import { AiBadge, Button, Card, DataKindBadge, Screen, SectionHeader, StatusChip } from '../../components';
import type { AgentStackParamList } from '../../navigation/types';
import { colors, spacing, typography } from '../../theme/tokens';
import { money } from '../../utils/money';

type Route = RouteProp<AgentStackParamList, 'TransactionConfirm'>;
type Nav = NativeStackNavigationProp<AgentStackParamList, 'TransactionConfirm'>;

export function TransactionConfirmScreen() {
  const { params } = useRoute<Route>();
  const navigation = useNavigation<Nav>();
  const result = params.result;
  const currency = 'USD';

  return (
    <Screen title="Transaction complete" subtitle={`Reference ${result.reference}`}>
      <Card>
        <View style={styles.row}>
          <Text style={styles.label}>Status</Text>
          <StatusChip label={result.status} tone={result.status === 'COMPLETED' ? 'success' : 'warning'} />
        </View>
        <Detail label="Type" value={result.type.replace(/_/g, ' ')} />
        <Detail label="Amount" value={money(result.amount, currency)} />
        <Detail label="Fee" value={money(result.fee, currency)} />
        <Detail label="Commission" value={money(result.commission, currency)} />
        {result.replayed ? <Text style={styles.replay}>Idempotent replay — no duplicate posting.</Text> : null}
      </Card>

      {result.balancesAfter ? (
        <>
          <SectionHeader title="Balances after" />
          <Card>
            <View style={styles.badgeRow}>
              <DataKindBadge kind="ledger" />
            </View>
            <Detail label="E-float" value={money(result.balancesAfter.efloat, currency)} />
            <Detail label="Cash" value={money(result.balancesAfter.cash, currency)} />
            <Detail label="Commission payable" value={money(result.balancesAfter.commissionPayable, currency)} />
          </Card>
        </>
      ) : null}

      {result.fraud ? (
        <>
          <SectionHeader title="Fraud check" />
          <Card>
            <View style={styles.badgeRow}>
              <AiBadge label="AI score" />
            </View>
            <Detail label="Risk" value={result.fraud.riskLevel} />
            <Detail label="Decision" value={result.fraud.decision} />
            <Detail label="Score" value={String(result.fraud.score)} />
            <Text style={styles.fraudExplanation}>{result.fraud.explanation}</Text>
          </Card>
        </>
      ) : null}

      <View style={styles.actions}>
        <Button
          label="Back to dashboard"
          onPress={() => navigation.navigate('AgentTabs', { screen: 'AgentHome' })}
        />
      </View>
    </Screen>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.detail}>
      <Text style={styles.detailLabel}>{label}</Text>
      <Text style={styles.detailValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.md,
  },
  label: { ...typography.caption, color: colors.inkMuted },
  detail: { marginBottom: spacing.sm, gap: spacing.xs },
  detailLabel: { ...typography.micro, color: colors.inkFaint, textTransform: 'uppercase' },
  detailValue: { ...typography.bodyStrong, color: colors.ink },
  badgeRow: { flexDirection: 'row', marginBottom: spacing.sm, gap: spacing.xs },
  replay: { ...typography.caption, color: colors.info, marginTop: spacing.sm },
  fraudExplanation: { ...typography.body, color: colors.inkMuted, marginTop: spacing.sm },
  actions: { marginTop: spacing.xl },
});