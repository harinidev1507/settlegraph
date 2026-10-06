import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../context/useAuth";
import { apiErrorMessage } from "../api/errors";

export default function SignupPage() {
  const [username, setUsername] = useState("");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const { register } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    try {
      await register(username, name, email, password);
      navigate("/dashboard");
    } catch (err) {
      setError(apiErrorMessage(err, "Signup failed"));
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="brand">
          <h1>SettleGraph</h1>
          <p className="tagline">Create your account</p>
        </div>
        <form onSubmit={handleSubmit} className="auth-form">
          <label className="field">
            <span>Username</span>
            <input placeholder="priya.s" value={username}
                   onChange={(e) => setUsername(e.target.value)} required />
          </label>
          <label className="field">
            <span>How do we call you?</span>
            <input placeholder="Priya Sharma" value={name}
                   onChange={(e) => setName(e.target.value)} required />
          </label>
          <label className="field">
            <span>Email</span>
            <input type="email" placeholder="you@example.com" value={email}
                   onChange={(e) => setEmail(e.target.value)} required />
          </label>
          <label className="field">
            <span>Password</span>
            <input type="password" placeholder="Min 6 characters" value={password}
                   onChange={(e) => setPassword(e.target.value)} required />
          </label>
          {error && <p className="error">{error}</p>}
          <button type="submit">Sign up</button>
        </form>
        <p className="auth-switch">Already have an account? <Link to="/login">Log in</Link></p>
      </div>
    </div>
  );
}
