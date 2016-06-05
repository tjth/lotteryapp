for d in participants/*/ ; do (cd "$d" && touch log && ./build_and_run_app.sh &> log); done

