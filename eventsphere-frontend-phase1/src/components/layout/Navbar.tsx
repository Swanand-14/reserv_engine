import { NavLink } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export function Navbar() {
  const { user, logout } = useAuth();
  const isOrganizer = user?.roles.includes("ORGANIZER");

  return (
    <nav className="app-navbar">
      <NavLink to="/browse" className="app-navbar__brand">
        <span className="mark">●</span> EventSphere
      </NavLink>

      <div className="app-navbar__links">
        <NavLink to="/browse" className={({ isActive }) => (isActive ? "active" : "")}>
          Now Showing
        </NavLink>
        {/* My Reservations link is added once that page exists (next phase) */}
        {isOrganizer && (
          <NavLink to="/dashboard" className={({ isActive }) => (isActive ? "active" : "")}>
            Organizer Studio
          </NavLink>
        )}
      </div>

      <div className="app-navbar__user">
        <span className="app-navbar__email">{user?.email}</span>
        <button className="secondary" onClick={() => logout()}>
          Log out
        </button>
      </div>
    </nav>
  );
}
