import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';
import { Button } from './Button';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Uncaught error in React component tree:', error, errorInfo);
  }

  public handleReset = () => {
    this.setState({ hasError: false, error: null });
    window.location.reload();
  };

  public render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-[var(--c-bg)] flex items-center justify-center p-4">
          <div className="bg-white rounded-xl border border-gray-200/80 shadow-md p-6 max-w-md w-full text-center space-y-4">
            <div className="w-12 h-12 bg-rose-50 text-rose-600 rounded-xl flex items-center justify-center mx-auto">
              <AlertTriangle className="w-6 h-6" />
            </div>
            <div>
              <h3 className="text-base font-bold text-gray-900">Đã xảy ra lỗi giao diện</h3>
              <p className="text-xs text-gray-500 mt-1">
                {this.state.error?.message || 'Có sự cố ngoài dự kiến trong quá trình xử lý dữ liệu.'}
              </p>
            </div>
            <div className="pt-2">
              <Button
                variant="primary"
                size="sm"
                className="w-full"
                leftIcon={<RefreshCw className="w-4 h-4" />}
                onClick={this.handleReset}
              >
                Tải lại trang
              </Button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
