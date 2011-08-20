So the goal in the svnmerge plugin is to assist feature branches, and in my dictionary, that needs to support bi-directional merges.

Unfortunately, even with Subversion merge improvements in 1.5/1.6, this still seems unnecessarily hard.

That is, if the sync is mostly one way (for example, you just keep pulling changes from the trunk to a feature branch and only merge back in the end), then it works OK. But when changes start flowing in both directions, one gets strange conflict errors --- sometimes "merge --reintegrate" works but not plain "merge", sometimes the other way around.

To better understand this problem. I did a little experiment, whose scripts I kept here. In search of atomic set of operations that enable bi-directional merges, I compared the following primitives:


 "i": ./integrate.sh
   This integrates from the trunk to branch. First it runs "svn merge" without --reintegrate to pull in changes
   in the branch to the master, then it runs "svn merge --record-only" to record this commit
   as merge commit in the branch
 
 "r": ./rebase.sh --reintegrate
   This runs "svn merge" without --reintegrate to pull changes in the trunk to the branch
 
 "R": ./rebase.sh
   This runs "svn merge --rebase" to pull changes in the trunk to the branch

The following order was legal:

          2nd op
           |iRr
         --+---
          i|xoo
  1st op  R|oox
          r|oox




FURTHER READING
---------------
Subversion merge

Easy reads
 - http://jugalps.wordpress.com/2009/07/31/svn-branching-and-merging-in-scrum/
 - http://blogs.collab.net/subversion/2008/07/subversion-merg/

Internals
 - http://www.collab.net/community/subversion/articles/merge-info.html
 - http://svnbook.red-bean.com/en/1.6/svn.branchmerge.advanced.html#svn.branchmerge.advanced.finalword

Related Q&As
 - http://stackoverflow.com/questions/3309602/subversion-branch-reintegration-in-v1-6
 - http://stackoverflow.com/questions/102472/subversion-branch-reintegration


