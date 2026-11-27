import { AxiosError } from 'axios';
import { ApiErrorBody } from '../types/api';

export class ApiError extends Error {
  status?: number;
  errorCode?: string;
  details?: Record<string, unknown>;

  constructor(message: string, status?: number, errorCode?: string, details?: Record<string, unknown>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.errorCode = errorCode;
    this.details = details;
  }
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (error instanceof AxiosError) {
    const body = error.response?.data as ApiErrorBody | undefined;
    return new ApiError(
      body?.message || error.message || 'Request failed',
      error.response?.status ?? body?.status,
      body?.errorCode,
      body?.details,
    );
  }
  if (error instanceof Error) {
    return new ApiError(error.message);
  }
  return new ApiError('Unexpected error');
}

export function errorMessage(error: unknown): string {
  return toApiError(error).message;
}
