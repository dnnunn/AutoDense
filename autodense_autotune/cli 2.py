import argparse, json, sys
from .runner import autotune
from .persistence import OptimizationPersistence

def cmd_run(args):
    """Run optimization with persistence tracking."""
    # Get run_cmd from spec file or from command line
    run_cmd = getattr(args, 'run_cmd', None)
    
    summary = autotune(
        spec_path=args.spec, 
        input_path=args.input, 
        workdir=args.workdir, 
        run_cmd=run_cmd,  # Can be None, runner will use spec
        mode=args.mode
    )
    if args.print_summary:
        print(json.dumps(summary, indent=2))
    return summary

def cmd_history(args):
    """Show optimization history."""
    persistence = OptimizationPersistence(workdir=args.workdir + "/optimization")
    sessions = persistence.get_session_history(task=args.task, input_hash=args.input_hash)
    
    if args.format == "json":
        print(json.dumps(sessions, indent=2))
    else:
        # Human-readable format
        print(f"{'Session ID':<25} {'Task':<15} {'Status':<12} {'Start Time':<20} {'Best Metrics'}")
        print("-" * 100)
        for session in sessions:
            start_time = session.get('start_time', 0)
            best_metrics = session.get('best_metrics', {})
            metrics_str = ', '.join([f"{k}={v:.3f}" for k, v in best_metrics.items()][:3])  # Show first 3 metrics
            if len(best_metrics) > 3:
                metrics_str += "..."
            
            print(f"{session['session_id']:<25} {session['task']:<15} {session['status']:<12} {start_time:<20.0f} {metrics_str}")

def cmd_analytics(args):
    """Show optimization analytics."""
    persistence = OptimizationPersistence(workdir=args.workdir + "/optimization")
    analytics = persistence.get_optimization_analytics(task=args.task)
    
    print("=== Optimization Analytics ===\n")
    
    print("Session Statistics:")
    for status, count in analytics['session_stats'].items():
        print(f"  {status}: {count}")
    print(f"  Total: {analytics['total_sessions']}\n")
    
    print("Attempt Statistics:")
    for result, count in analytics['attempt_stats'].items():
        print(f"  {result}: {count}")
    print(f"  Total: {analytics['total_attempts']}\n")
    
    if analytics['patch_type_stats']:
        print("Most Common Patch Types:")
        for patch_type, count in analytics['patch_type_stats'].items():
            print(f"  {patch_type}: {count}")
    
    if analytics['total_attempts'] > 0:
        success_rate = analytics['attempt_stats'].get('successful', 0) / analytics['total_attempts'] * 100
        print(f"\nOverall Success Rate: {success_rate:.1f}%")

def cmd_report(args):
    """Export detailed session report."""
    persistence = OptimizationPersistence(workdir=args.workdir + "/optimization")
    report_path = persistence.export_session_report(args.session_id, args.output)
    print(f"Detailed report exported to: {report_path}")

def cmd_attempts(args):
    """Show attempts for a specific session."""
    persistence = OptimizationPersistence(workdir=args.workdir + "/optimization")
    attempts = persistence.get_session_attempts(args.session_id)
    
    if args.format == "json":
        print(json.dumps(attempts, indent=2))
    else:
        print(f"{'Attempt':<12} {'Patch Type':<12} {'Success':<8} {'Error':<50}")
        print("-" * 90)
        for attempt in attempts:
            patch_type = attempt.get('patch_proposal', {}).get('kind', 'unknown')
            success = "✓" if attempt['success'] else "✗"
            error = attempt.get('error_message', '')[:47] + "..." if len(attempt.get('error_message', '')) > 50 else attempt.get('error_message', '')
            print(f"{attempt['sequence_number']:<12} {patch_type:<12} {success:<8} {error:<50}")

def main():
    parser = argparse.ArgumentParser(description="AutoDense Autotune CLI")
    parser.add_argument("--workdir", default="runs", help="Work directory")
    
    subparsers = parser.add_subparsers(dest="command", help="Available commands")
    
    # Run optimization command
    run_parser = subparsers.add_parser("run", help="Run optimization")
    run_parser.add_argument("--spec", required=True, help="Path to spec.yaml")
    run_parser.add_argument("--input", required=True, help="Input image/data file")
    run_parser.add_argument("--run-cmd", help="Override command from spec.yaml. Use {input} and {outdir} placeholders.")
    run_parser.add_argument("--mode", default="grid", choices=["grid"], help="Search mode")
    run_parser.add_argument("--print-summary", action="store_true")
    
    # History command
    history_parser = subparsers.add_parser("history", help="Show optimization history")
    history_parser.add_argument("--task", help="Filter by task type")
    history_parser.add_argument("--input-hash", help="Filter by input hash")
    history_parser.add_argument("--format", choices=["table", "json"], default="table", help="Output format")
    
    # Analytics command
    analytics_parser = subparsers.add_parser("analytics", help="Show optimization analytics")
    analytics_parser.add_argument("--task", help="Filter by task type")
    
    # Report command
    report_parser = subparsers.add_parser("report", help="Export detailed session report")
    report_parser.add_argument("--session-id", required=True, help="Session ID to export")
    report_parser.add_argument("--output", help="Output file path")
    
    # Attempts command
    attempts_parser = subparsers.add_parser("attempts", help="Show attempts for a session")
    attempts_parser.add_argument("--session-id", required=True, help="Session ID to show attempts for")
    attempts_parser.add_argument("--format", choices=["table", "json"], default="table", help="Output format")
    
    args = parser.parse_args()
    
    if not args.command:
        parser.print_help()
        return
    
    try:
        if args.command == "run":
            cmd_run(args)
        elif args.command == "history":
            cmd_history(args)
        elif args.command == "analytics":
            cmd_analytics(args)
        elif args.command == "report":
            cmd_report(args)
        elif args.command == "attempts":
            cmd_attempts(args)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
