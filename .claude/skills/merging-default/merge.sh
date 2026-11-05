#!/usr/bin/env bash
#
# Merges the current branch into the default branch with --no-ff.
# Prints one word on stdout: MERGED (0), CONFLICT (1), DIRTY (2) or ERROR (3).
# Diagnostics go to stderr. It never pushes.

set -u

finish() {
  echo "$1"
  case "$1" in
    MERGED) exit 0 ;;
    CONFLICT) exit 1 ;;
    DIRTY) exit 2 ;;
    *) exit 3 ;;
  esac
}

fail() {
  echo "merge.sh: $1" >&2
  finish ERROR
}

detect_default_branch() {
  local head
  head=$(git symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>/dev/null)
  if [ -z "$head" ]; then
    head=$(git remote show origin 2>/dev/null | sed -n 's/.*HEAD branch: //p')
  fi
  head=${head#origin/}
  if [ "$head" = "(unknown)" ]; then
    head=""
  fi
  echo "$head"
}

git rev-parse --is-inside-work-tree >/dev/null 2>&1 || fail "not inside a Git repository"

branch=$(git symbolic-ref --quiet --short HEAD) || fail "HEAD is detached"

if [ -n "$(git status --porcelain)" ]; then
  git status --short >&2
  finish DIRTY
fi

git fetch --quiet origin >&2 || fail "git fetch failed"

default_branch=$(detect_default_branch)
[ -n "$default_branch" ] || fail "could not detect the default branch"
[ "$branch" != "$default_branch" ] || fail "already on the default branch $default_branch"

git show-ref --verify --quiet "refs/heads/$default_branch" \
  || fail "no local branch $default_branch"

behind=$(git rev-list --count "$default_branch..origin/$default_branch" 2>/dev/null) \
  || fail "no remote branch origin/$default_branch"
[ "$behind" -eq 0 ] || fail "$default_branch is $behind commit(s) behind origin"

if git merge-base --is-ancestor "$branch" "$default_branch"; then
  fail "$branch has nothing to merge into $default_branch"
fi

subject=${1:-"chore: merge $branch into $default_branch"}
body=${2:-}

git checkout --quiet "$default_branch" >&2 || fail "could not check out $default_branch"

if [ -n "$body" ]; then
  git merge --no-ff --no-edit -m "$subject" -m "$body" "$branch" >&2
else
  git merge --no-ff --no-edit -m "$subject" "$branch" >&2
fi
merge_status=$?

if [ "$merge_status" -eq 0 ]; then
  finish MERGED
fi

conflicted=$(git ls-files --unmerged)
if git rev-parse --quiet --verify MERGE_HEAD >/dev/null; then
  git merge --abort >&2
fi
git checkout --quiet "$branch" >&2

if [ -n "$conflicted" ]; then
  finish CONFLICT
fi
fail "git merge failed"
