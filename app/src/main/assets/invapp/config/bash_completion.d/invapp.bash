# bash completion for the InVxTermux container helpers
# Installed into $PREFIX/etc/bash_completion.d/ (bash-completion is in the base
# package list, which sources this directory automatically).

_td_dev() {
    local cur
    cur="${COMP_WORDS[COMP_CWORD]}"
    if [ -f package.json ]; then
        COMPREPLY=( $(compgen -W "$(bun -e 'const s=require("./package.json").scripts||{};console.log(Object.keys(s).join(" "))' 2>/dev/null)" -- "$cur") )
    else
        COMPREPLY=( $(compgen -W "dev build preview test" -- "$cur") )
    fi
}
complete -F _td_dev td-dev

_td_scaffold() {
    COMPREPLY=( $(compgen -W "vanilla vanilla-ts react react-ts vue vue-ts pwa pwa-react --here --no-install" -- "${COMP_WORDS[COMP_CWORD]}") )
}
complete -F _td_scaffold td-scaffold

_td_clone() {
    COMPREPLY=( $(compgen -W "--bun-i --bun-install" -- "${COMP_WORDS[COMP_CWORD]}") )
}
complete -F _td_clone td-clone

_td_ai() {
    COMPREPLY=( $(compgen -W "4096 5000 8080" -- "${COMP_WORDS[COMP_CWORD]}") )
}
complete -F _td_ai td-ai

_td_info() { :; }
complete -F _td_info td-info td-upgrade td-install-extras bun-doctor
