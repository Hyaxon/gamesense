# ADR: Use TanStack Query for frontend API caching

## Status

Accepted

## Context

The Upcoming Games list needs to retrieve scheduled games from the Java API and
reuse recent responses across renders and navigation. It also needs loading and
error states. React state and Context can share data, but implementing request
deduplication, freshness, retries, and cancellation ourselves adds maintenance work.

The backend already stores canonical data in memory. A server cache such as Redis
would not prevent the browser from making repeated HTTP requests. Browser storage
such as localStorage supplies persistence but does not manage request lifecycles.

## Decision

Add `@tanstack/react-query` as a frontend runtime dependency. Create one
module-level QueryClient in `src/main.jsx` and expose it through
QueryClientProvider above the router. This preserves the same cache across React
renders and client-side navigation.

Keep HTTP requests in the service layer, response mapping in utility functions,
and query configuration in hooks. Presentation components receive data and request
states. Query keys must include parameters that change the response, such as game
status, so upcoming games and future history queries do not share an entry.

Use the library's in-memory cache without persistence for now. Configure freshness,
retention, retries, and refetch behavior explicitly when implementing each query;
installing the provider alone neither fetches games nor prevents repeated requests.
The upcoming-games hook and page integration are subsequent work. Breakdown API
integration is outside this change.

TanStack Query provides the server-data lifecycle this application needs without
introducing Redux. SWR is another viable option; we choose TanStack Query for its
explicit query keys and controls for freshness, cancellation, and invalidation.

## Consequences

Components can share cached API responses without maintaining a custom cache.
The additional dependency increases the frontend bundle and requires dependency
updates and familiarity with query behavior. Incorrect keys or freshness settings
can still produce stale data or unnecessary requests.

The cache is local to each running browser app and resets on a full page reload.
It does not provide offline persistence or a cache shared between users. Freshness
must reflect the data's update frequency; caching does not guarantee current data.
Tests of query hooks should create isolated clients to avoid shared test state.

See the [official setup documentation](https://tanstack.com/query/latest/docs/framework/react/quick-start).
