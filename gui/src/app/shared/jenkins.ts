export function jenkinsBuildUrl(
  jobUrl: string | null | undefined,
  build: number | null | undefined,
): string | null {
  const job = jobUrl?.trim();
  if (!job || build === null || build === undefined) {
    return null;
  }
  return `${job.endsWith('/') ? job : `${job}/`}${build}/`;
}
