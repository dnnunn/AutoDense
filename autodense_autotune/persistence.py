"""
Enhanced persistence system for AutoDense optimization workflows.

This module provides comprehensive state management for iterative parameter optimization,
enabling session resumption, performance tracking, and optimization history analysis.
"""

from typing import Dict, List, Optional, Any, Tuple
from dataclasses import dataclass, field, asdict
from pathlib import Path
import json
import sqlite3
import time
import hashlib
from datetime import datetime, timezone

from .schemas import RunReport, PatchProposal, Critique, Spec

@dataclass
class OptimizationSession:
    """Represents a complete optimization session with all attempts and results."""
    session_id: str
    task: str
    input_path: str
    input_hash: str
    spec_path: str
    start_time: float
    end_time: Optional[float] = None
    status: str = "running"  # running, completed, failed, halted
    initial_metrics: Dict[str, float] = field(default_factory=dict)
    best_metrics: Dict[str, float] = field(default_factory=dict)
    attempts: List[Dict[str, Any]] = field(default_factory=list)
    final_parameters: Dict[str, Any] = field(default_factory=dict)

@dataclass 
class OptimizationAttempt:
    """Represents a single parameter change attempt within an optimization session."""
    attempt_id: str
    session_id: str
    sequence_number: int
    patch_proposal: Dict[str, Any]
    critique: Optional[Dict[str, Any]] = None
    before_metrics: Dict[str, float] = field(default_factory=dict)
    after_metrics: Dict[str, float] = field(default_factory=dict)
    metric_deltas: Dict[str, float] = field(default_factory=dict)
    success: bool = False
    error_message: Optional[str] = None
    timestamp: float = field(default_factory=time.time)
    branch_name: Optional[str] = None

class OptimizationPersistence:
    """Comprehensive persistence manager for optimization workflows."""
    
    def __init__(self, workdir: str = "audits/optimization"):
        self.workdir = Path(workdir)
        self.workdir.mkdir(parents=True, exist_ok=True)
        self.db_path = self.workdir / "optimization_history.db"
        self._init_database()
        
    def _init_database(self):
        """Initialize SQLite database for optimization history."""
        conn = sqlite3.connect(self.db_path)
        try:
            conn.executescript('''
                CREATE TABLE IF NOT EXISTS optimization_sessions (
                    session_id TEXT PRIMARY KEY,
                    task TEXT NOT NULL,
                    input_path TEXT NOT NULL,
                    input_hash TEXT NOT NULL,
                    spec_path TEXT NOT NULL,
                    start_time REAL NOT NULL,
                    end_time REAL,
                    status TEXT NOT NULL,
                    initial_metrics TEXT,
                    best_metrics TEXT,
                    final_parameters TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                
                CREATE TABLE IF NOT EXISTS optimization_attempts (
                    attempt_id TEXT PRIMARY KEY,
                    session_id TEXT NOT NULL,
                    sequence_number INTEGER NOT NULL,
                    patch_proposal TEXT NOT NULL,
                    critique TEXT,
                    before_metrics TEXT,
                    after_metrics TEXT,
                    metric_deltas TEXT,
                    success BOOLEAN NOT NULL,
                    error_message TEXT,
                    timestamp REAL NOT NULL,
                    branch_name TEXT,
                    FOREIGN KEY (session_id) REFERENCES optimization_sessions (session_id)
                );
                
                CREATE INDEX IF NOT EXISTS idx_session_task ON optimization_sessions(task);
                CREATE INDEX IF NOT EXISTS idx_session_input_hash ON optimization_sessions(input_hash);
                CREATE INDEX IF NOT EXISTS idx_attempts_session ON optimization_attempts(session_id);
                CREATE INDEX IF NOT EXISTS idx_attempts_success ON optimization_attempts(success);
            ''')
            conn.commit()
        finally:
            conn.close()
    
    def generate_session_id(self, task: str, input_path: str, spec_path: str) -> str:
        """Generate unique session ID based on task, input, and timestamp."""
        timestamp = str(int(time.time()))
        content = f"{task}:{input_path}:{spec_path}:{timestamp}"
        hash_suffix = hashlib.sha256(content.encode()).hexdigest()[:8]
        return f"{task}_{timestamp}_{hash_suffix}"
    
    def create_session(self, task: str, input_path: str, input_hash: str, 
                      spec_path: str, initial_metrics: Dict[str, float]) -> str:
        """Create new optimization session and return session ID."""
        session_id = self.generate_session_id(task, input_path, spec_path)
        session = OptimizationSession(
            session_id=session_id,
            task=task,
            input_path=input_path,
            input_hash=input_hash,
            spec_path=spec_path,
            start_time=time.time(),
            initial_metrics=initial_metrics,
            best_metrics=initial_metrics.copy()
        )
        
        # Store in database
        conn = sqlite3.connect(self.db_path)
        try:
            conn.execute('''
                INSERT INTO optimization_sessions 
                (session_id, task, input_path, input_hash, spec_path, start_time, 
                 status, initial_metrics, best_metrics, final_parameters)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                session_id, task, input_path, input_hash, spec_path, session.start_time,
                session.status, json.dumps(initial_metrics), json.dumps(initial_metrics), 
                json.dumps({})
            ))
            conn.commit()
        finally:
            conn.close()
            
        # Also store JSON file for human readability
        session_file = self.workdir / f"session_{session_id}.json"
        session_file.write_text(json.dumps(asdict(session), indent=2), encoding="utf-8")
        
        return session_id
    
    def record_attempt(self, session_id: str, patch_proposal: PatchProposal,
                      critique: Optional[Critique] = None,
                      before_metrics: Dict[str, float] = None,
                      after_metrics: Dict[str, float] = None,
                      success: bool = False,
                      error_message: Optional[str] = None,
                      branch_name: Optional[str] = None) -> str:
        """Record an optimization attempt."""
        
        # Get next sequence number
        conn = sqlite3.connect(self.db_path)
        try:
            cursor = conn.execute(
                'SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM optimization_attempts WHERE session_id = ?',
                (session_id,)
            )
            sequence_number = cursor.fetchone()[0]
        finally:
            conn.close()
        
        # Generate attempt ID
        attempt_id = f"{session_id}_attempt_{sequence_number:03d}"
        
        # Calculate metric deltas if both metrics available
        metric_deltas = {}
        if before_metrics and after_metrics:
            for key in before_metrics:
                if key in after_metrics:
                    metric_deltas[key] = after_metrics[key] - before_metrics[key]
        
        # Create attempt record
        attempt = OptimizationAttempt(
            attempt_id=attempt_id,
            session_id=session_id,
            sequence_number=sequence_number,
            patch_proposal=asdict(patch_proposal),
            critique=asdict(critique) if critique else None,
            before_metrics=before_metrics or {},
            after_metrics=after_metrics or {},
            metric_deltas=metric_deltas,
            success=success,
            error_message=error_message,
            branch_name=branch_name
        )
        
        # Store in database
        conn = sqlite3.connect(self.db_path)
        try:
            conn.execute('''
                INSERT INTO optimization_attempts
                (attempt_id, session_id, sequence_number, patch_proposal, critique,
                 before_metrics, after_metrics, metric_deltas, success, error_message,
                 timestamp, branch_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                attempt_id, session_id, sequence_number, 
                json.dumps(attempt.patch_proposal),
                json.dumps(attempt.critique) if attempt.critique else None,
                json.dumps(attempt.before_metrics),
                json.dumps(attempt.after_metrics),
                json.dumps(attempt.metric_deltas),
                success, error_message, attempt.timestamp, branch_name
            ))
            conn.commit()
        finally:
            conn.close()
        
        return attempt_id
    
    def update_session_completion(self, session_id: str, status: str,
                                 best_metrics: Dict[str, float],
                                 final_parameters: Dict[str, Any]):
        """Mark session as completed with final results."""
        conn = sqlite3.connect(self.db_path)
        try:
            conn.execute('''
                UPDATE optimization_sessions 
                SET end_time = ?, status = ?, best_metrics = ?, final_parameters = ?
                WHERE session_id = ?
            ''', (
                time.time(), status, json.dumps(best_metrics), 
                json.dumps(final_parameters), session_id
            ))
            conn.commit()
        finally:
            conn.close()
        
        # Update JSON file
        session_file = self.workdir / f"session_{session_id}.json"
        if session_file.exists():
            session_data = json.loads(session_file.read_text(encoding="utf-8"))
            session_data['end_time'] = time.time()
            session_data['status'] = status
            session_data['best_metrics'] = best_metrics
            session_data['final_parameters'] = final_parameters
            session_file.write_text(json.dumps(session_data, indent=2), encoding="utf-8")
    
    def get_session_history(self, task: Optional[str] = None, 
                           input_hash: Optional[str] = None) -> List[Dict[str, Any]]:
        """Retrieve optimization session history with optional filtering."""
        conn = sqlite3.connect(self.db_path)
        try:
            query = 'SELECT * FROM optimization_sessions'
            params = []
            
            conditions = []
            if task:
                conditions.append('task = ?')
                params.append(task)
            if input_hash:
                conditions.append('input_hash = ?') 
                params.append(input_hash)
                
            if conditions:
                query += ' WHERE ' + ' AND '.join(conditions)
            
            query += ' ORDER BY start_time DESC'
            
            cursor = conn.execute(query, params)
            columns = [desc[0] for desc in cursor.description]
            
            sessions = []
            for row in cursor.fetchall():
                session_dict = dict(zip(columns, row))
                # Parse JSON fields
                for field in ['initial_metrics', 'best_metrics', 'final_parameters']:
                    if session_dict[field]:
                        session_dict[field] = json.loads(session_dict[field])
                sessions.append(session_dict)
                
            return sessions
        finally:
            conn.close()
    
    def get_session_attempts(self, session_id: str) -> List[Dict[str, Any]]:
        """Get all attempts for a specific session."""
        conn = sqlite3.connect(self.db_path)
        try:
            cursor = conn.execute('''
                SELECT * FROM optimization_attempts 
                WHERE session_id = ? 
                ORDER BY sequence_number
            ''', (session_id,))
            
            columns = [desc[0] for desc in cursor.description]
            attempts = []
            
            for row in cursor.fetchall():
                attempt_dict = dict(zip(columns, row))
                # Parse JSON fields
                for field in ['patch_proposal', 'critique', 'before_metrics', 
                             'after_metrics', 'metric_deltas']:
                    if attempt_dict[field]:
                        attempt_dict[field] = json.loads(attempt_dict[field])
                attempts.append(attempt_dict)
                
            return attempts
        finally:
            conn.close()
    
    def get_optimization_analytics(self, task: Optional[str] = None) -> Dict[str, Any]:
        """Generate analytics on optimization performance."""
        conn = sqlite3.connect(self.db_path)
        try:
            # Session success rates
            session_query = '''
                SELECT status, COUNT(*) as count 
                FROM optimization_sessions
            '''
            params = []
            if task:
                session_query += ' WHERE task = ?'
                params.append(task)
            session_query += ' GROUP BY status'
            
            session_stats = {}
            for status, count in conn.execute(session_query, params):
                session_stats[status] = count
            
            # Attempt success rates
            attempt_query = '''
                SELECT success, COUNT(*) as count
                FROM optimization_attempts a
                JOIN optimization_sessions s ON a.session_id = s.session_id
            '''
            if task:
                attempt_query += ' WHERE s.task = ?'
            attempt_query += ' GROUP BY success'
            
            attempt_stats = {}
            for success, count in conn.execute(attempt_query, params):
                attempt_stats['successful' if success else 'failed'] = count
            
            # Most common patch types
            patch_type_query = '''
                SELECT json_extract(patch_proposal, '$.kind') as patch_type,
                       COUNT(*) as count
                FROM optimization_attempts a
                JOIN optimization_sessions s ON a.session_id = s.session_id
            '''
            if task:
                patch_type_query += ' WHERE s.task = ?'
            patch_type_query += ' GROUP BY patch_type ORDER BY count DESC'
            
            patch_type_stats = {}
            for patch_type, count in conn.execute(patch_type_query, params):
                if patch_type:
                    patch_type_stats[patch_type] = count
            
            return {
                'session_stats': session_stats,
                'attempt_stats': attempt_stats,  
                'patch_type_stats': patch_type_stats,
                'total_sessions': sum(session_stats.values()),
                'total_attempts': sum(attempt_stats.values())
            }
        finally:
            conn.close()
    
    def find_similar_optimizations(self, input_hash: str, task: str) -> List[Dict[str, Any]]:
        """Find previous optimization sessions for the same input/task combination."""
        return self.get_session_history(task=task, input_hash=input_hash)
    
    def export_session_report(self, session_id: str, output_path: Optional[str] = None) -> str:
        """Export comprehensive session report for analysis."""
        # Get session data
        sessions = self.get_session_history()
        session = next((s for s in sessions if s['session_id'] == session_id), None)
        if not session:
            raise ValueError(f"Session {session_id} not found")
        
        attempts = self.get_session_attempts(session_id)
        
        # Generate comprehensive report
        report = {
            'session_info': session,
            'attempts': attempts,
            'summary': {
                'total_attempts': len(attempts),
                'successful_attempts': len([a for a in attempts if a['success']]),
                'optimization_duration': session.get('end_time', time.time()) - session['start_time'],
                'best_improvement': self._calculate_best_improvement(session, attempts)
            },
            'generated_at': datetime.now(timezone.utc).isoformat()
        }
        
        # Write to file
        if not output_path:
            output_path = self.workdir / f"report_{session_id}.json"
        else:
            output_path = Path(output_path)
            
        output_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
        return str(output_path)
    
    def _calculate_best_improvement(self, session: Dict[str, Any], 
                                  attempts: List[Dict[str, Any]]) -> Dict[str, float]:
        """Calculate the best metric improvements achieved."""
        initial_metrics = session.get('initial_metrics', {})
        best_metrics = session.get('best_metrics', {})
        
        improvements = {}
        for metric, initial_value in initial_metrics.items():
            if metric in best_metrics:
                improvement = best_metrics[metric] - initial_value
                improvements[metric] = improvement
                
        return improvements