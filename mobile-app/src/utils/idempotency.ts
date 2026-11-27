import uuid from 'react-native-uuid';

export function newIdempotencyKey(prefix = 'MOB'): string {
  return `${prefix}-${uuid.v4()}`;
}
