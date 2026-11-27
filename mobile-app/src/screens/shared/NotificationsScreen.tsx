import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';
import { StyleSheet, View } from 'react-native';
import { sharedApi } from '../../api/endpoints';
import { errorMessage } from '../../api/errors';
import { Button, EmptyState, ErrorBanner, ListRow, LoadingBlock, Screen, StatusChip } from '../../components';
import { colors, spacing } from '../../theme/tokens';

function severityTone(severity?: string) {
  if (severity === 'CRITICAL' || severity === 'HIGH') return 'danger' as const;
  if (severity === 'MEDIUM') return 'warning' as const;
  return 'info' as const;
}

export function NotificationsScreen() {
  const qc = useQueryClient();
  const query = useQuery({
    queryKey: ['notifications'],
    queryFn: sharedApi.notifications,
  });
  const markAll = useMutation({
    mutationFn: sharedApi.markAllRead,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });
  const markOne = useMutation({
    mutationFn: (id: string) => sharedApi.markRead(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const items = query.data?.content ?? [];

  return (
    <Screen
      title="Notifications"
      subtitle="Alerts from the platform"
      refreshing={query.isRefetching}
      onRefresh={() => query.refetch()}>
      {query.isError ? <ErrorBanner message={errorMessage(query.error)} /> : null}
      {query.isLoading ? <LoadingBlock label="Loading notifications…" /> : null}
      {!query.isLoading && items.length === 0 ? (
        <EmptyState title="No notifications" message="You are up to date." />
      ) : null}
      {items.length > 0 ? (
        <View style={styles.actions}>
          <Button
            label="Mark all read"
            variant="secondary"
            loading={markAll.isPending}
            onPress={() => markAll.mutate()}
          />
        </View>
      ) : null}
      <View style={styles.list}>
        {items.map((n) => (
          <View key={n.id} style={styles.item}>
            <ListRow
              title={n.title}
              subtitle={`${n.message}\n${format(new Date(n.createdAt), 'dd MMM yyyy HH:mm')}`}
              value={n.read ? 'Read' : 'New'}
              onPress={() => {
                if (!n.read) markOne.mutate(n.id);
              }}
            />
            {!n.read && n.severity ? (
              <StatusChip label={n.severity} tone={severityTone(n.severity)} />
            ) : null}
          </View>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  actions: { marginBottom: spacing.md },
  list: { gap: spacing.sm },
  item: {
    backgroundColor: colors.white,
    borderRadius: 12,
    paddingHorizontal: spacing.md,
    paddingBottom: spacing.sm,
  },
});
