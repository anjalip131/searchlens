'use client';

import { useQuery } from '@tanstack/react-query';
import { EvaluationRun } from '../types';

const API = process.env.NEXT_PUBLIC_API_URL;

export default function HistoryPage() {
  const { data: runs, isLoading, error } = useQuery<EvaluationRun[]>({
    queryKey: ['runs'],
    queryFn:  () => fetch(`${API}/api/runs`).then(r => r.json()),
  });

  if (isLoading) return <p className="text-gray-500">Loading...</p>;
  if (error)     return <p className="text-red-500">Failed to load history.</p>;

  return (
    <div className="max-w-5xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-800 mb-6">Evaluation History</h1>

      <div className="bg-white rounded-xl shadow overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500 text-left">
            <tr>
              <th className="px-4 py-3">Query</th>
              <th className="px-4 py-3">Avg Score</th>
              <th className="px-4 py-3">Results</th>
              <th className="px-4 py-3">Latency</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3">Run At</th>
            </tr>
          </thead>
          <tbody>
            {runs?.map(run => (
              <tr key={run.runId} className="border-t hover:bg-gray-50 transition-colors">
                <td className="px-4 py-3 font-medium text-gray-800">{run.queryText}</td>
                <td className="px-4 py-3">
                  <span className="font-semibold text-blue-600">
                    {run.avgScore ? run.avgScore.toFixed(2) : '—'} / 3
                  </span>
                </td>
                <td className="px-4 py-3 text-gray-500">{run.totalEvaluated}</td>
                <td className="px-4 py-3 text-gray-500">{run.latencyMs ? `${run.latencyMs}ms` : '—'}</td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-1 rounded-full text-xs font-semibold ${
                    run.status === 'complete' ? 'bg-green-100 text-green-700' :
                    run.status === 'failed'   ? 'bg-red-100 text-red-700' :
                                               'bg-yellow-100 text-yellow-700'
                  }`}>
                    {run.status}
                  </span>
                </td>
                <td className="px-4 py-3 text-gray-400 text-xs">
                  {new Date(run.startedAt).toLocaleString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
