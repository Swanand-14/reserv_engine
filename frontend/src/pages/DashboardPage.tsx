import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export function DashboardPage() {
  const { user, logout } = useAuth();
  const isOrganizer = user?.roles.includes("ORGANIZER");

  return (
    <div>
      <nav className="top-bar">
        <strong>reserv-engine</strong>
        <button className="secondary" onClick={() => logout()}>Log out</button>
      </nav>

      <div className="page">
        <h1>Welcome, {user?.email}</h1>
        <p className="muted">Roles: {user?.roles.join(", ")}</p>

        {isOrganizer ? (
          <div className="role-grid">
            <div className="role-card">
              <h3>Organizer</h3>
              <ul>
                <li><Link to="/organizer/venues">Venues</Link></li>
                <li><Link to="/organizer/events">Events</Link></li>
              </ul>
            </div>
          </div>
        ) : (
          <p className="muted">No organizer access yet.</p>
        )}
      </div>
    </div>
  );
}