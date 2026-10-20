import { resolve } from '$app/paths';

/** The deployment sub-path ('' locally, '/handsup' on GitHub Pages). */
export const base: string = (resolve as unknown as (path: string) => string)('/').replace(/\/$/, '');
