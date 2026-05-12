alter table public.job_history
    drop column if exists logs;

update public.job_template
set params_template = params_template || jsonb_build_object(
        'artifactPath', 'target/*.jar',
        'uploadUrl', '',
        'repositoryUsername', '',
        'repositoryPassword', '',
        'contentType', 'application/java-archive'
)
where path = 'build/maven';

update public.job_template
set params_template = params_template || jsonb_build_object(
        'artifactPath', 'build/libs/*.jar',
        'uploadUrl', '',
        'repositoryUsername', '',
        'repositoryPassword', '',
        'contentType', 'application/java-archive'
)
where path = 'build/gradle';

update public.job_template
set params_template = params_template || jsonb_build_object(
        'artifactPath', '',
        'uploadUrl', '',
        'repositoryUsername', '',
        'repositoryPassword', '',
        'contentType', 'application/octet-stream'
)
where path in ('build/javac', 'build/gcc');
