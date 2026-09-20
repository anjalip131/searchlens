export interface EvaluationResult {
  rank:           number;
  productId:      number;
  productName:    string;
  relevanceScore: 0 | 1 | 2 | 3;
  reasoning:      string;
}

export interface EvaluationRun {
  runId:          number;
  queryId:        number;
  queryText:      string;
  status:         'pending' | 'running' | 'complete' | 'failed';
  avgScore:       number;
  latencyMs:      number;
  totalEvaluated: number;
  startedAt:      string;
  results?:       EvaluationResult[];
}

export interface TrendPoint {
  category:   string;
  day:        string;
  avgScore:   number;
  totalEvals: number;
}
