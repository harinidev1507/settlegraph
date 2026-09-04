import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { apiErrorMessage } from "../api/errors";

export default function LoginPage() {
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const { login } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    try {
      await login(identifier, password);
      navigate("/dashboard");
    } catch (err) {
      setError(apiErrorMessage(err, "Login failed"));
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="brand">
          <h1>SettleGraph</h1>
          <p className="tagline">Fewer transactions. Fully settled.</p>
        </div>
        <form onSubmit={handleSubmit} className="auth-form">
          <label className="field">
            <span>Email or username</span>
            <input placeholder="you@example.com or username" value={identifier}
                   onChange={(e) => setIdentifier(e.target.value)} required />
          </label>
          <label className="field">
            <span>Password</span>
            <input type="password" placeholder="••••••••" value={password}
                   onChange={(e) => setPassword(e.target.value)} required />
          </label>
          {error && <p className="error">{error}</p>}
          <button type="submit">Log in</button>
        </form>
        <p className="auth-switch">New here? <Link to="/signup">Create an account</Link></p>
      </div>
    </div>
  );
}
