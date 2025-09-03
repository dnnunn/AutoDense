import React from 'react';

type Severity = 'info' | 'warn' | 'error';

type HintAction = {
  label: string;
  op: 'rerun_step' | 'preview_overlay_diff' | 'open_docs';
  step_id?: string;
  params_delta?: Record<string, any>;
  docs_id?: string;
};

type Hint = {
  id: string;
  severity: Severity;
  confidence: number;
  applies_to: 'sds' | 'etbr' | 'colony';
  symptoms: string[];
  why_it_matters: string;
  suggested_changes: Record<string, any>;
  actions: HintAction[];
  docs?: string;
  dismissible_for_session: boolean;
};

export function CoachBanner({ hints, onAction, onDismiss }:{
  hints: Hint[];
  onAction: (action: HintAction) => void;
  onDismiss?: (id: string) => void;
}) {
  if (!hints || hints.length === 0) return null;
  return (
    <div style={{position:'sticky', top:0, zIndex:50}}>
      {hints.map(h => (
        <div key={h.id} role={h.severity === 'error' ? 'alert' : 'status'}
             style={{
               margin:'8px', padding:'12px 14px', borderRadius:12,
               background: h.severity==='error' ? '#2a0f12' : h.severity==='warn' ? '#2a1f0f' : '#0f1f2a',
               border: '1px solid rgba(255,255,255,0.12)'
             }}>
          <div style={{display:'flex', gap:12, alignItems:'start', justifyContent:'space-between'}}>
            <div style={{flex:1}}>
              <div style={{fontWeight:700, fontSize:14}}>
                {h.severity.toUpperCase()} · {h.id.replace('HINT_','').replaceAll('_',' ')} (conf {Math.round(h.confidence*100)}%)
              </div>
              <div style={{opacity:0.9, fontSize:14, marginTop:4}}>{h.why_it_matters}</div>
              {Object.keys(h.suggested_changes || {}).length > 0 && (
                <div style={{opacity:0.8, fontSize:12, marginTop:6}}>
                  Params: {Object.entries(h.suggested_changes).map(([k,v])=> `${k}→${String(v)}`).join(', ')}
                </div>
              )}
            </div>
            <div style={{display:'flex', gap:8}}>
              {h.actions.map((a, i) => (
                <button key={i}
                        onClick={() => onAction(a)}
                        style={{padding:'8px 10px', borderRadius:10, border:'1px solid rgba(255,255,255,0.18)', background:'transparent', cursor:'pointer'}}>
                  {a.label}
                </button>
              ))}
              {h.dismissible_for_session && (
                <button onClick={() => onDismiss?.(h.id)}
                        aria-label="Dismiss hint"
                        style={{padding:'8px 10px', borderRadius:10, border:'1px solid rgba(255,255,255,0.18)', background:'transparent', cursor:'pointer'}}>
                  Dismiss
                </button>
              )}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}