import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import Providers from './providers';
import Link from 'next/link';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'SearchLens',
  description: 'AI-powered search relevance evaluator',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className={inter.className}>
        <Providers>
          <nav className="bg-gray-900 text-white px-6 py-4 flex gap-6 items-center">
            <span className="font-bold text-lg">🔍 SearchLens</span>
            <Link href="/"        className="hover:text-blue-400 transition-colors">Evaluate</Link>
            <Link href="/history" className="hover:text-blue-400 transition-colors">History</Link>
            <Link href="/trends"  className="hover:text-blue-400 transition-colors">Trends</Link>
          </nav>
          <main className="min-h-screen bg-gray-50 p-6">{children}</main>
        </Providers>
      </body>
    </html>
  );
}
