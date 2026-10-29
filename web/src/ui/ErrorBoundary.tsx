import { Component, type ErrorInfo, type ReactNode } from "react";
import { Button } from "./kit";

interface Props {
  children: ReactNode;
}
interface State {
  error: Error | null;
}

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("UI crash", error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return (
        <div className="error-box" role="alert" style={{ margin: 24 }}>
          <strong>Something went wrong</strong>
          <p>{this.state.error.message || "The page failed to render. Retry without leaving this portal."}</p>
          <Button kind="ghost" onClick={() => this.setState({ error: null })}>
            Retry
          </Button>
        </div>
      );
    }
    return this.props.children;
  }
}
