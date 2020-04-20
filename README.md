# mmtool
The mycore migration tool is a simple command line util to migrate mycore applications with overwritten resources to newer versions.

You pass a file of your overwriten resources and then:
- The tool searches in MyCoRe, MIR and in other configured projects for the "base" file
- The tool displays a diff from the old to new version, so you can see all changes which were made to the file

## configuration
The configuration should be located at your $user.home/.mmtool/config.json
```json
{
  "applications": [
    {
      "projectHome": "/home/paschty/workspace/reposis_digibib/",
      "baseVersion": "2018.06.0.x",
      "targetVersion": "2019.06.x"
    }
  ],
  "projects": ["mir","mycore"]
}
```

Applications contains all apps that are installed for your user.
The ProjectHome is your mycore home directory.
Base version is the branch on which the resource in projectHome are based.
Target version is the branch they should be based.

Projects contains all git projects which can contain base files. You need to check them out with git to $user.home/.mmtool/$projectName.
