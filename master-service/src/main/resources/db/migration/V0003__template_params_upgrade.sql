update public.job_template
set params_template = jsonb_build_object(
        'url', '',
        'branch', '',
        'login', '',
        'password', '',
        'targetDir', 'source'
)
where path = 'vsc/git';

update public.job_template
set params_template = jsonb_build_object(
        'url', '',
        'branch', '',
        'login', '',
        'password', '',
        'targetDir', 'source'
)
where path = 'vsc/mercurial';

update public.job_template
set params_template = jsonb_build_object(
        'goals', 'clean package -DskipTests',
        'args', '',
        'workDir', '.'
)
where path = 'build/maven';

update public.job_template
set params_template = jsonb_build_object(
        'tasks', 'build',
        'args', '',
        'workDir', '.',
        'useWrapper', true
)
where path = 'build/gradle';

update public.job_template
set params_template = jsonb_build_object(
        'args', '-version',
        'workDir', '.'
)
where path = 'build/javac';

update public.job_template
set params_template = jsonb_build_object(
        'args', '--version',
        'workDir', '.'
)
where path = 'build/gcc';
