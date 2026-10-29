/**
 * Login shell (web). Desktop/laptop: circular coin that flips in.
 * Mobile-width viewports: same flip on the previous rounded-rectangle card
 * (see styles.css @media max-width 820px). Authentication is unchanged.
 */
import type { ReactNode } from "react";

export default function CoinLogin({
  back,
  logo,
  role,
  school,
  hint,
  children,
}: {
  back: ReactNode;
  logo: ReactNode;
  role: string;
  school: string;
  hint: string;
  children: ReactNode;
}) {
  return (
    <div className="coin-stage">
      <div className="coin-stage-back">{back}</div>
      <div className="coin-login" data-experimental="coin-login">
        <div className="coin-login-inner">
          <div className="coin-login-logo">{logo}</div>
          <p className="coin-login-school">{school}</p>
          <h1 className="coin-login-role">{role}</h1>
          <p className="coin-login-hint">{hint}</p>
          <div className="coin-login-fields">{children}</div>
        </div>
      </div>
    </div>
  );
}
