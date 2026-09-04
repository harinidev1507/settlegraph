// Pull a human-readable message out of an Axios error. The backend always
// responds with `{ error: "..." }` (see GlobalExceptionHandler), so prefer that.
// When there's no response at all the request never reached the server —
// usually the API isn't running — so say that instead of a vague fallback.
export function apiErrorMessage(err, fallback) {
  if (err?.response) {
    return err.response.data?.error || fallback;
  }
  if (err?.request) {
    return "Can't reach the server. Is the API running?";
  }
  return fallback;
}
