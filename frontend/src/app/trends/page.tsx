'use client';

import { useQuery } from '@tanstack/react-query';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer, BarChart, Bar
} from 'recharts';
import { TrendPoint } from '../types';

const API = process.env.NEXT_PUBLIC_API_URL;
const COLORS = ['#3b82f6','#10b981','#f59e0b','#ef4444','#8b5cf6'];

export default function TrendsPage() {
  const { data, isLoading, error } = useQuery<TrendPoint[]>({
    queryKey: ['trends'],
    queryFn:  () => fetch(`${API}/api/trends`).then(r => r.json()),
  });

  if (isLoading) return <p className="text-gray-500">Loading...</p>;
  if (error)     return <p className="text-red-500">Failed to load trends.</p>;
  if (!data?.length) return <p className="text-gray-500">No data yet. Run some evaluations first.</p>;

  // Group by category for line chart
  const categories = [...new Set(data.map(d => d.category))];
  const days        = [...new Set(data.map(d => d.day.split('T')[0]))].sort();

  const lineData = days.map(day => {
    const point: Record<string, any> = { day };
    categories.forEach(cat => {
      const match = data.find(d => d.day.startsWith(day) && d.category === cat);
      point[cat] = match ? match.avgScore : null;
    });
    return point;
  });

  // Score distribution for bar chart
  const barData = categories.map(cat => {
    const catPoints = data.filter(d => d.category === cat);
    const avg = catPoints.reduce((s, d) => s + d.avgScore, 0) / catPoints.length;
    return { category: cat, avgScore: parseFloat(avg.toFixed(2)) };
  });

  return (
    <div className="max-w-5xl mx-auto space-y-8">
      <h1 className="text-2xl font-bold text-gray-800">Relevance Trends</h1>

      <div className="bg-white rounded-xl shadow p-6">
        <h2 className="text-lg font-semibold text-gray-700 mb-4">Avg Score Over Time (by Category)</h2>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={lineData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="day" tick={{ fontSize: 12 }} />
            <YAxis domain={[0, 3]} tick={{ fontSize: 12 }} />
            <Tooltip />
            <Legend />
            {categories.map((cat, i) => (
              <Line key={cat} type="monotone" dataKey={cat}
                stroke={COLORS[i % COLORS.length]} strokeWidth={2} connectNulls />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="bg-white rounded-xl shadow p-6">
        <h2 className="text-lg font-semibold text-gray-700 mb-4">Avg Score by Category</h2>
        <ResponsiveContainer width="100%" height={250}>
          <BarChart data={barData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="category" tick={{ fontSize: 12 }} />
            <YAxis domain={[0, 3]} tick={{ fontSize: 12 }} />
            <Tooltip />
            <Bar dataKey="avgScore" fill="#3b82f6" radius={[4,4,0,0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
