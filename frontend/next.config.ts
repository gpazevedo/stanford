// next.config.ts
// Note: 'output: export' was removed because the /courses/[courseId] dynamic
// route fetches data client-side and cannot provide generateStaticParams.
// Amplify Hosting SSR is used instead (baseDirectory: .next).
import type { NextConfig } from 'next';

const nextConfig: NextConfig = {};

export default nextConfig;
