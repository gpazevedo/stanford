// src/app/layout.tsx  — Server Component (no 'use client')
import type { Metadata } from 'next';
import { ClientProviders } from './ClientProviders';
import './globals.css';

export const metadata: Metadata = {
  title: 'Stanford CS-PMN Course Finder',
  description: 'Discover and apply to CS Professional Master\'s courses',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-gray-50">
        <ClientProviders>{children}</ClientProviders>
      </body>
    </html>
  );
}
