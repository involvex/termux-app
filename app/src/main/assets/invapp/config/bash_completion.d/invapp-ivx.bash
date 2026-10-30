# bash completion for ivx-configure / ivx-doctor
# Installed into $PREFIX/etc/bash_completion.d/ and sourced by
# $PREFIX/etc/profile.d/invapp-completions.sh (device) or bash-completion.
#
# The COMPREPLY=( $(compgen ...) ) idiom is the documented completion pattern.
# shellcheck disable=SC2207

_ivx_configure() {
    local cur prev
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD-1]}"

    case "$prev" in
        banner)
            COMPREPLY=( $(compgen -W "on off preview reset style color text show" -- "$cur") )
            return
            ;;
        style)
            COMPREPLY=( $(compgen -W "pixelfont small mini plain auto" -- "$cur") )
            return
            ;;
        color|colour)
            COMPREPLY=( $(compgen -W "green cyan magenta white rainbow none" -- "$cur") )
            return
            ;;
        completions|completion)
            COMPREPLY=( $(compgen -W "list all clean git gh npm pnpm docker kubectl helm terraform deno tailscale cargo rustup just uv bun" -- "$cur") )
            return
            ;;
        packages|pkg)
            COMPREPLY=( $(compgen -W "list essentials quality build network all" -- "$cur") )
            return
            ;;
        ai)
            COMPREPLY=( $(compgen -W "status opencode agent" -- "$cur") )
            return
            ;;
        doctor)
            COMPREPLY=( $(compgen -W "--strict --network" -- "$cur") )
            return
            ;;
        --font)
            COMPREPLY=( $(compgen -f -- "$cur") )
            return
            ;;
        --font-file)
            COMPREPLY=( $(compgen -f -- "$cur") )
            return
            ;;
    esac

    if [ "$COMP_CWORD" -eq 1 ]; then
        COMPREPLY=( $(compgen -W "status banner completions packages doctor ai reset help" -- "$cur") )
        return
    fi

    COMPREPLY=( $(compgen -W "--yes --dry-run --no-color --font --font-file" -- "$cur") )
}
complete -F _ivx_configure ivx-configure

_ivx_doctor() {
    local cur
    cur="${COMP_WORDS[COMP_CWORD]}"
    COMPREPLY=( $(compgen -W "--strict --network" -- "$cur") )
}
complete -F _ivx_doctor ivx-doctor
