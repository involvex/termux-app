# bash completion for ivx-configure / ivx-doctor / ivx-cleanup
# Installed into $PREFIX/etc/bash_completion.d/ and sourced by
# $PREFIX/etc/profile.d/invapp-completions.sh (device) or bash-completion.
#
# The COMPREPLY=( $(compgen ...) ) idiom is the documented completion pattern.
# shellcheck disable=SC2207

_ivx_configure() {
    local cur prev words cword
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD-1]}"
    words=("${COMP_WORDS[@]}")
    cword=$COMP_CWORD

    # Nested: network dns|proxy|goproxy …
    if [ "$cword" -ge 2 ] && { [ "${words[1]}" = "network" ] || [ "${words[1]}" = "net" ]; }; then
        case "${words[2]:-}" in
            dns)
                if [ "$cword" -eq 3 ]; then
                    COMPREPLY=( $(compgen -W "status fix set" -- "$cur") )
                fi
                return
                ;;
            proxy)
                if [ "$cword" -eq 3 ]; then
                    COMPREPLY=( $(compgen -W "status set socks clear" -- "$cur") )
                fi
                return
                ;;
            goproxy)
                if [ "$cword" -eq 3 ]; then
                    COMPREPLY=( $(compgen -W "status preset set clear" -- "$cur") )
                elif [ "$cword" -eq 4 ] && [ "${words[3]}" = "preset" ]; then
                    COMPREPLY=( $(compgen -W "google goproxyio direct" -- "$cur") )
                fi
                return
                ;;
            go)
                if [ "$cword" -eq 3 ]; then
                    COMPREPLY=( $(compgen -W "status wrap unwrap" -- "$cur") )
                fi
                return
                ;;
            *)
                if [ "$cword" -eq 2 ]; then
                    COMPREPLY=( $(compgen -W "status dns proxy goproxy go apply test" -- "$cur") )
                fi
                return
                ;;
        esac
    fi

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
        network|net)
            COMPREPLY=( $(compgen -W "status dns proxy goproxy go apply test" -- "$cur") )
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
        COMPREPLY=( $(compgen -W "status banner completions packages network doctor ai reset help" -- "$cur") )
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

_ivx_cleanup() {
    local cur
    cur="${COMP_WORDS[COMP_CWORD]}"
    if [ "$COMP_CWORD" -eq 1 ]; then
        COMPREPLY=( $(compgen -W "pkg cargo bun npm pip uv help --yes --dry-run --no-color" -- "$cur") )
        return
    fi
    COMPREPLY=( $(compgen -W "--yes --dry-run --no-color" -- "$cur") )
}
complete -F _ivx_cleanup ivx-cleanup

_ivx_disk_analyze() {
    local cur
    cur="${COMP_WORDS[COMP_CWORD]}"
    if [ "$COMP_CWORD" -eq 1 ]; then
        COMPREPLY=( $(compgen -W "overview df prefix home guests large help --top= --no-color" -- "$cur") )
        return
    fi
    if [ "${COMP_WORDS[1]}" = "large" ]; then
        COMPREPLY=( $(compgen -d -- "$cur") )
        return
    fi
    COMPREPLY=( $(compgen -W "--top= --no-color" -- "$cur") )
}
complete -F _ivx_disk_analyze ivx-disk-analyze
