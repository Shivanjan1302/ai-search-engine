export function isTokenLike(value: string | null | undefined): value is string {
  if (!value) return false;
  const parts = value.split('.');
  return parts.length === 3 && parts.every(part => part.trim().length > 0);
}
