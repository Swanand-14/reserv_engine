import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import { ProtectedRoute } from "./components/layout/ProtectedRoute";
import { AppLayout } from "./components/layout/AppLayout";
import { LoadingState } from "./components/ui/LoadingState";
import { SignupPage } from "./pages/SignupPage";
import { LoginPage } from "./pages/LoginPage";
import { DashboardPage } from "./pages/DashboardPage";
import { BrowsePage } from "./pages/customer/BrowsePage";
import { VenuesPage } from "./pages/organizer/VenuesPage";
import { EventsPage } from "./pages/organizer/EventsPage";

// Authenticated users land on Browse; everyone else lands on Login.
// Used for both "/" and any unmatched path.
function RootRedirect() {
  const { user, loading } = useAuth();
  if (loading) return <LoadingState />;
  return <Navigate to={user ? "/browse" : "/login"} replace />;
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<RootRedirect />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/login" element={<LoginPage />} />

          <Route
            path="/browse"
            element={
              <ProtectedRoute>
                <AppLayout>
                  <BrowsePage />
                </AppLayout>
              </ProtectedRoute>
            }
          />

          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/organizer/venues"
            element={
              <ProtectedRoute>
                <VenuesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/organizer/events"
            element={
              <ProtectedRoute>
                <EventsPage />
              </ProtectedRoute>
            }
          />

          <Route path="*" element={<RootRedirect />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
