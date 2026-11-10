import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ArrowRight, Check, ChevronDown, Copy, LogOut, ShieldCheck, UserRound, UserPlus, X } from 'lucide-react';
import { request } from './api';
import './account.css';

export default function AccountMenu({ user, onSignOut }) {
  const [open, setOpen] = useState(false);
  const [dialog, setDialog] = useState('');
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteUrl, setInviteUrl] = useState('');
  const [inviteError, setInviteError] = useState('');
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const menuRef = useRef(null);
  const inviteLinkRef = useRef(null);
  const isAdmin = user?.role === 'ADMIN';
  const roleLabel = isAdmin ? 'Workspace administrator' : 'Learner account';

  useEffect(() => {
    if (!open) return undefined;
    const dismiss = event => {
      if (!menuRef.current?.contains(event.target)) setOpen(false);
    };
    const escape = event => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', dismiss);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('mousedown', dismiss);
      document.removeEventListener('keydown', escape);
    };
  }, [open]);

  useEffect(() => {
    if (!dialog) return undefined;
    const escape = event => {
      if (event.key === 'Escape' && !busy) setDialog('');
    };
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [dialog, busy]);

  const closeDialog = () => {
    if (busy) return;
    setDialog('');
    setInviteError('');
  };

  const createInvite = async event => {
    event.preventDefault();
    setInviteError('');
    setCopied(false);
    setBusy(true);
    try {
      const result = await request('/api/auth/admin/invites', {
        method: 'POST',
        body: { email: inviteEmail.trim() },
      });
      const link = new URL('/', window.location.origin);
      link.searchParams.set('invite', result.token);
      link.searchParams.set('email', result.email);
      setInviteUrl(link.toString());
    } catch (error) {
      setInviteError(error.message || 'Could not create the invitation. Please try again.');
    } finally {
      setBusy(false);
    }
  };

  const copyInvite = async () => {
    if (!inviteUrl) return;
    try {
      await navigator.clipboard.writeText(inviteUrl);
      setCopied(true);
      setInviteError('');
    } catch {
      inviteLinkRef.current?.focus();
      inviteLinkRef.current?.select();
      setInviteError('Copy is unavailable here. The invitation link is selected so you can copy it.');
    }
  };

  return <>
    <div className="account-control" ref={menuRef}>
      <button
        type="button"
        className="account-trigger"
        aria-label="Open account profile and actions"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen(value => !value)}
      >
        <span className="top-avatar">{user?.email?.slice(0, 1)?.toUpperCase() || 'U'}</span>
        <span className="account-trigger-label">Account</span>
        <ChevronDown size={14}/>
      </button>
      {open && <section className="account-menu" role="dialog" aria-label="Account profile and actions">
        <div className="account-menu-head">
          <span className="account-menu-eyebrow">SIGNED IN</span>
          <strong>{user?.email}</strong>
          <span className={`account-role ${isAdmin ? 'admin' : ''}`}><ShieldCheck size={13}/>{roleLabel}</span>
        </div>
        <button type="button" className="account-action" onClick={() => { setDialog('profile'); setOpen(false); }}>
          <UserRound size={15}/><span>Profile details</span><ArrowRight size={14}/>
        </button>
        {isAdmin && <button type="button" className="account-action" onClick={() => {
          setInviteEmail(''); setInviteUrl(''); setInviteError(''); setCopied(false);
          setDialog('invite'); setOpen(false);
        }}>
          <UserPlus size={15}/><span>Invite a learner</span><ArrowRight size={14}/>
        </button>}
        <button type="button" className="account-signout" onClick={onSignOut}><LogOut size={15}/> Sign out</button>
      </section>}
    </div>

    {dialog && createPortal(<div className="account-dialog-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) closeDialog(); }}>
      {dialog === 'profile' ? <section className="account-dialog" role="dialog" aria-modal="true" aria-labelledby="account-profile-title">
        <header className="account-dialog-head">
          <div><span className="account-dialog-eyebrow">YOUR PREPTRACK ACCOUNT</span><h2 id="account-profile-title">Profile details</h2></div>
          <button type="button" className="account-close" aria-label="Close profile" onClick={closeDialog}><X size={17}/></button>
        </header>
        <div className="account-profile-fields">
          <div><span>Email address</span><strong>{user?.email}</strong></div>
          <div><span>Access level</span><strong>{roleLabel}</strong></div>
          <div><span>Workspace</span><strong>My workspace</strong></div>
        </div>
        <p className="account-dialog-note"><ShieldCheck size={15}/> Your learning progress belongs to this account. Access to PrepTrack requires your approved Tailscale network.</p>
        <footer className="account-dialog-actions"><button type="button" className="account-secondary" onClick={closeDialog}>Close</button><button type="button" className="account-signout" onClick={onSignOut}><LogOut size={15}/> Sign out</button></footer>
      </section> : <section className="account-dialog" role="dialog" aria-modal="true" aria-labelledby="account-invite-title">
        <header className="account-dialog-head">
          <div><span className="account-dialog-eyebrow">WORKSPACE ACCESS</span><h2 id="account-invite-title">Invite a learner</h2></div>
          <button type="button" className="account-close" aria-label="Close invite dialog" onClick={closeDialog}><X size={17}/></button>
        </header>
        {!inviteUrl ? <form className="account-invite-form" onSubmit={createInvite}>
          <p>Invite someone by email. They will create their own password and get a separate private progress account.</p>
          <label>Email address<input required type="email" maxLength={254} autoComplete="email" value={inviteEmail} onChange={event => { setInviteEmail(event.target.value); setInviteUrl(''); }} placeholder="learner@example.com"/></label>
          {inviteError && <div className="account-error" role="alert">{inviteError}</div>}
          <p className="account-dialog-note"><ShieldCheck size={15}/> The invite expires in 7 days and works only for this email. The learner must first join your Tailscale network. This link does not enable public internet access.</p>
          <footer className="account-dialog-actions"><button type="button" className="account-secondary" disabled={busy} onClick={closeDialog}>Cancel</button><button type="submit" className="account-primary" disabled={busy}>{busy ? 'Creating invite…' : 'Create invite'} <ArrowRight size={15}/></button></footer>
        </form> : <div className="account-invite-result">
          <div className="account-invite-success"><Check size={16}/><div><strong>Invitation ready</strong><span>Created for {inviteEmail.trim()}</span></div></div>
          <label>Share this link<input ref={inviteLinkRef} readOnly value={inviteUrl} onFocus={event => event.target.select()} aria-label="Invitation link"/></label>
          {inviteError && <div className="account-error" role="status">{inviteError}</div>}
          <p className="account-dialog-note">Share it privately with the invited learner. It can be used once and expires in 7 days. They will choose a password when they accept.</p>
          <footer className="account-dialog-actions"><button type="button" className="account-secondary" onClick={closeDialog}>Done</button><button type="button" className="account-primary" onClick={copyInvite}>{copied ? <><Check size={15}/> Copied</> : <><Copy size={15}/> Copy invite link</>}</button></footer>
        </div>}
      </section>}
    </div>, document.body)}
  </>;
}
