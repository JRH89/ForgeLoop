/**
 * Signs the operator out with a same-origin POST. The control plane rejects GET /logout so that another
 * site cannot sign operators out through a link or image. The form uses `display: contents`, so the
 * button keeps the layout and placement classes the former link had.
 */
export function SignOutButton({ className }: { className: string }) {
  return <form className="signout-form" method="post" action="/logout">
    <button type="submit" className={`logout ${className}`}>Sign out</button>
  </form>;
}
