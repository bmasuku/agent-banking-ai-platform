import { StyleSheet, Text, View } from 'react-native';
import { BarChart, LineChart } from 'react-native-gifted-charts';
import { colors, spacing, typography } from '../theme/tokens';
import { AiBadge, DataKindBadge } from './AiBadge';
import { Card } from './Card';

type Point = { label: string; value: number };

type FloatTrendChartProps = {
  title: string;
  subtitle?: string;
  points: Point[];
  kind?: 'line' | 'bar';
  maxBars?: number;
  /** Live ledger series vs AI trajectory. */
  dataKind?: 'ledger' | 'predicted';
};

export function FloatTrendChart({
  title,
  subtitle,
  points,
  kind = 'line',
  maxBars = 8,
  dataKind = 'ledger',
}: FloatTrendChartProps) {
  const data = points.slice(0, maxBars).map((p) => ({
    value: Math.max(0, Number(p.value) || 0),
    label: p.label,
  }));

  if (data.length === 0) {
    return null;
  }

  return (
    <Card style={[styles.card, dataKind === 'predicted' ? styles.predicted : undefined]}>
      <View style={styles.header}>
        <View style={styles.titles}>
          <Text style={styles.title}>{title}</Text>
          {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
        </View>
        <View style={styles.badges}>
          <DataKindBadge kind={dataKind === 'predicted' ? 'predicted' : 'ledger'} />
          {dataKind === 'predicted' ? <AiBadge /> : null}
        </View>
      </View>
      <View style={styles.chart}>
        {kind === 'bar' ? (
          <BarChart
            data={data}
            barWidth={22}
            spacing={16}
            roundedTop
            frontColor={dataKind === 'predicted' ? colors.info : colors.forestSoft}
            yAxisThickness={0}
            xAxisThickness={1}
            xAxisColor={colors.line}
            yAxisTextStyle={styles.axis}
            xAxisLabelTextStyle={styles.axis}
            noOfSections={4}
            isAnimated
            height={140}
          />
        ) : (
          <LineChart
            data={data}
            curved
            thickness={3}
            color={dataKind === 'predicted' ? colors.info : colors.forest}
            dataPointsColor={dataKind === 'predicted' ? colors.info : colors.forestDeep}
            startFillColor={dataKind === 'predicted' ? colors.infoSoft : colors.mintStrong}
            endFillColor={dataKind === 'predicted' ? colors.infoSoft : colors.mint}
            startOpacity={0.45}
            endOpacity={0.05}
            areaChart
            yAxisThickness={0}
            xAxisThickness={1}
            xAxisColor={colors.line}
            yAxisTextStyle={styles.axis}
            xAxisLabelTextStyle={styles.axis}
            noOfSections={4}
            isAnimated
            height={140}
          />
        )}
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { marginBottom: spacing.lg },
  predicted: {
    borderColor: colors.info,
    borderWidth: StyleSheet.hairlineWidth,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: spacing.md,
    marginBottom: spacing.md,
  },
  titles: { flex: 1, gap: spacing.xs },
  badges: { flexDirection: 'row', gap: spacing.xs, alignItems: 'flex-start' },
  title: { ...typography.subtitle, color: colors.ink },
  subtitle: { ...typography.caption, color: colors.inkMuted },
  chart: { overflow: 'hidden' },
  axis: { color: colors.inkFaint, fontSize: 10 },
});
