'use client';

import { useState } from 'react';
import { EvaluationResult } from './types';

const API = process.env.NEXT_PUBLIC_API_URL;

const SCORE_COLORS: Record<number, string> = {
  3: 'bg-green-100 text-green-800',
  2: 'bg-blue-100 text-blue-800',
  1: 'bg-yellow-100 text-yellow-800',
  0: 'bg-red-100 text-red-800',
};

const SCORE_LABELS: Record<number, string> = {
  3: 'Perfect',
  2: 'Mostly relevant',
  1: 'Tangential',
  0: 'Not relevant',
};

export default function Home() {
  const [query,   setQuery]   = useState('');
  const [category, setCategory] = useState('');
  const [loading,  setLoading]  = useState(false);
  const [results,  setResults]  = useState<EvaluationResult[] | null>(null);
  const [latency,  setLatency]  = useState<number | null>(null);
  const [error,    setError]    = useState<string | null>(null);

  async function handleEvaluate() {
    if (!query.trim()) return;
    setLoading(true);
    setError(null);
    setResults(null);

    try {
      const res = await fetch(`${API}/api/evaluate`, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ query, categoryHint: category || null }),
      });
      if (res.status === 404) throw new Error("No products found for this query"); if (!res.ok) throw new Error('Evaluation failed');
      const data = await res.json();
      setResults(data.results);
      setLatency(data.latencyMs);
    } catch (e: any) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-3xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-800 mb-6">Search Relevance Evaluator</h1>

      <div className="bg-white rounded-xl shadow p-6 mb-6">
        <div className="flex gap-3 mb-4">
          <input
            className="flex-1 border-2 border-gray-300 rounded-lg px-4 py-2 text-gray-900 placeholder-gray-400 focus:outline-none focus:border-blue-500"
            placeholder="e.g. noise cancelling headphones"
            value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleEvaluate()}
          />
          <input
            className="w-44 border-2 border-gray-300 rounded-lg px-4 py-2 text-gray-900 placeholder-gray-400 focus:outline-none focus:border-blue-500"
            placeholder="Category (optional)"
            value={category}
            onChange={e => setCategory(e.target.value)}
          />
        </div>
        <button
          onClick={handleEvaluate}
          disabled={loading || !query.trim()}
          className="w-full bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white font-semibold py-2 rounded-lg transition-colors"
        >
          {loading ? 'Evaluating...' : 'Run Evaluation'}
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4 mb-6">
          {error}
        </div>
      )}

      {results && (
        <div className="bg-white rounded-xl shadow p-6">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-semibold text-gray-700">Results</h2>
            {latency && <span className="text-sm text-gray-400">{latency}ms</span>}
          </div>

          {results.length === 0 ? (
            <p className="text-gray-500">No products found for this query.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500 border-b">
                  <th className="pb-2 w-8">#</th>
                  <th className="pb-2">Product</th>
                  <th className="pb-2 w-36">Score</th>
                  <th className="pb-2">Reasoning</th>
                </tr>
              </thead>
              <tbody>
                {results.map(r => (
                  <tr key={r.rank} className="border-b last:border-0">
                    <td className="py-3 text-gray-400">{r.rank}</td>
                    <td className="py-3 font-medium text-gray-800">{r.productName}</td>
                    <td className="py-3">
                      <span className={`px-2 py-1 rounded-full text-xs font-semibold ${SCORE_COLORS[r.relevanceScore]}`}>
                        {r.relevanceScore}/3 {SCORE_LABELS[r.relevanceScore]}
                      </span>
                    </td>
                    <td className="py-3 text-gray-600">{r.reasoning}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}
