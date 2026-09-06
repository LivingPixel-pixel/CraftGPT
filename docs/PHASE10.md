# Phase 10 — Plan review and transparent generation

Phase 10 makes the AI workflow understandable before and after money is spent.

## Plan review

After an initial plan or iteration is saved, CraftGPT opens a five-page review:

1. overview, target dimensions, orientation, estimated changes and cost
2. goals, required features and preferred features
3. material roles, candidates and purposes
4. constraints, avoided outcomes and assumptions
5. design rationale, implementation brief and detailed API usage

The review is intentionally read-only. **Revise plan** creates another immutable
plan version through the existing planning workflow. **Generate preview** uses the
currently active reviewed version.

The CraftBook main action now enters this review when a plan exists but no preview
has been generated. Commands remain available and retain their existing validation.

## Preflight estimates

Planning and builder requests expose a character-based token range before sending.
For the built-in GPT-5.6 Sol, Terra and Luna presets, CraftGPT converts this range
to a dollar range using a pricing snapshot checked on 2026-07-28.

The range is deliberately approximate:

- UTF-8 request size is converted with both conservative and optimistic text ratios.
- expected structured-output size is bounded by the request's output-token limit.
- no caching discount is assumed before a request.
- custom models show tokens but never receive a guessed dollar price.

The provider's measured usage and billing remain authoritative.

## Actual usage

Completed Responses-compatible calls retain:

- input tokens
- cached input tokens
- cache-write tokens when reported
- output tokens
- reasoning tokens
- total tokens
- wall-clock latency

Reasoning tokens are displayed as an output detail and are not billed a second time.
Optional `vN.usage.json` and `<build-id>.usage.json` sidecars persist measured
metadata next to the immutable plan/build files. Sidecar failures are logged but do
not make a valid plan or preview unloadable.

## Cancellation and preview review

Planning and builder futures can be cancelled from their respective screens.
Cancelling invalidates the request generation token, so late responses are ignored
and cannot overwrite current state.

Preview completeness now distinguishes generated data from camera visibility.
Blocks outside the current frustum or render distance do not make an otherwise
complete preview invalid. A renderer-cap omission still requires explicit
acknowledgement, and the player is instructed to move around the build before
accepting it.

## Manual checks

1. Generate a plan and inspect all five review pages at small and large UI scales.
2. Revise from the review and verify a new plan version is created.
3. Generate a preview from the reviewed plan and compare estimate with actual usage.
4. Restart the client and verify usage sidecars are displayed when available.
5. Cancel planning and builder calls, then confirm late results do not change state.
6. Inspect a preview from multiple angles and verify frustum culling does not force
   incomplete-preview confirmation.
7. Use a custom model ID and confirm tokens are shown without a dollar estimate.
