/**
 * Build-time feature switches, set in `.env` (and overridden by
 * `.env.production` for the deployed bundle).
 *
 * Unlike demo mode these are not switchable at runtime: a campaign decides
 * before a build which way of working its agents are on, and a URL parameter
 * that reopened the named-voter roll would defeat the point.
 */

/**
 * `VITE_PRE_ELECTION_ONLY=true` limits agents to the pre-election, house-level
 * flow (FR-U12): the Voters tab offers only "Pre-election sentiment", and the
 * named-voter roll, its change proposals and its sentiment screen are closed.
 *
 * This is a UI restriction. The agent APIs behind those screens stay open, so
 * it is a way of working rather than a security boundary — access to a booth is
 * still what the server enforces.
 */
export const PRE_ELECTION_ONLY = import.meta.env.VITE_PRE_ELECTION_ONLY === 'true';
